package com.applypilot.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.applypilot.TestcontainersConfiguration;
import com.applypilot.common.ai.ChatCompletion;
import com.applypilot.common.ai.ChatCompletionClient;
import com.applypilot.common.ai.LlmUnavailableException;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Analysis API against a real PostgreSQL. The model is a Mockito mock of
 * {@link ChatCompletionClient}, so these tests never call a real provider and always get the
 * same answer. Requires Docker to be running.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AnalysisFlowIT {

	private static final String MODEL_ANSWER = """
			{"company": "Acme", "roleTitle": "Backend Developer", "seniority": "SENIOR",
			 "requiredSkills": ["Java", "Spring Boot"], "niceToHaveSkills": ["Kubernetes"],
			 "workMode": "REMOTE", "location": "Lima, Peru", "languages": ["English"],
			 "salary": null, "summary": "Backend role building REST APIs with Spring Boot."}
			""";

	private static final String POSTING = """
			Acme is hiring a Senior Backend Developer (remote, Lima).
			Requirements: Java, Spring Boot. Nice to have: Kubernetes. English required.
			""";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AiUsageRepository aiUsage;

	@Autowired
	private TransactionTemplate transaction;

	@MockitoBean
	private ChatCompletionClient chatClient;

	@BeforeEach
	void modelAnswersValidJson() {
		when(chatClient.isConfigured()).thenReturn(true);
		when(chatClient.complete(any())).thenReturn(new ChatCompletion(MODEL_ANSWER, "openai/gpt-oss-120b", 900, 150));
	}

	@Test
	void analyzesStoresAndReturnsTheAnalysis() throws Exception {
		String token = registerUser();
		String applicationId = createApplication(token);

		mockMvc.perform(authorized(get(analysisUrl(applicationId)), token))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ANALYSIS_NOT_FOUND"));

		mockMvc.perform(authorized(post(analysisUrl(applicationId)), token).content(body(POSTING)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.applicationId").value(applicationId))
			.andExpect(jsonPath("$.source").value("LLM"))
			.andExpect(jsonPath("$.fallbackReason").doesNotExist())
			.andExpect(jsonPath("$.model").value("openai/gpt-oss-120b"))
			.andExpect(jsonPath("$.cached").value(false))
			.andExpect(jsonPath("$.seniority").value("SENIOR"))
			.andExpect(jsonPath("$.requiredSkills", contains("Java", "Spring Boot")))
			.andExpect(jsonPath("$.niceToHaveSkills", contains("Kubernetes")))
			.andExpect(jsonPath("$.workMode").value("REMOTE"))
			.andExpect(jsonPath("$.userId").doesNotExist());

		mockMvc.perform(authorized(get(analysisUrl(applicationId)), token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.company").value("Acme"))
			.andExpect(jsonPath("$.postingText").value(POSTING.strip()));
	}

	@Test
	void theSameTextIsAnalyzedByTheModelOnlyOncePerUser() throws Exception {
		String token = registerUser();
		String first = createApplication(token);
		String second = createApplication(token);
		clearInvocations(chatClient);

		mockMvc.perform(authorized(post(analysisUrl(first)), token).content(body(POSTING)))
			.andExpect(jsonPath("$.cached").value(false));
		mockMvc.perform(authorized(post(analysisUrl(second)), token).content(body("\n" + POSTING + "\n\n")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.cached").value(true))
			.andExpect(jsonPath("$.source").value("LLM"))
			.andExpect(jsonPath("$.applicationId").value(second));
		verify(chatClient, times(1)).complete(any());

		// The cache is per user: pasted text may be private, so another user gets a fresh call.
		String otherToken = registerUser();
		mockMvc.perform(authorized(post(analysisUrl(createApplication(otherToken))), otherToken).content(body(POSTING)))
			.andExpect(jsonPath("$.cached").value(false));
		verify(chatClient, times(2)).complete(any());
	}

	@Test
	void fallsBackToKeywordsWhenTheModelIsUnavailableOrNotConfigured() throws Exception {
		String token = registerUser();
		String applicationId = createApplication(token);

		when(chatClient.complete(any())).thenThrow(new LlmUnavailableException("timeout", null));
		mockMvc.perform(authorized(post(analysisUrl(applicationId)), token).content(body(POSTING)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.source").value("FALLBACK"))
			.andExpect(jsonPath("$.fallbackReason").value("PROVIDER_ERROR"))
			.andExpect(jsonPath("$.model").doesNotExist())
			.andExpect(jsonPath("$.requiredSkills", hasItem("Java")))
			.andExpect(jsonPath("$.niceToHaveSkills", contains("Kubernetes")));

		when(chatClient.isConfigured()).thenReturn(false);
		clearInvocations(chatClient);
		// A fallback result is never reused from the cache: the same text is extracted again.
		mockMvc.perform(authorized(post(analysisUrl(applicationId)), token).content(body(POSTING)))
			.andExpect(jsonPath("$.source").value("FALLBACK"))
			.andExpect(jsonPath("$.fallbackReason").value("NOT_CONFIGURED"))
			.andExpect(jsonPath("$.cached").value(false));
		verify(chatClient, never()).complete(any());
	}

	@Test
	void validatesThePostingText() throws Exception {
		String token = registerUser();
		String url = analysisUrl(createApplication(token));

		for (String invalid : List.of("", "too short", "x".repeat(20_001))) {
			mockMvc.perform(authorized(post(url), token).content(body(invalid)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("postingText"));
		}
		mockMvc.perform(authorized(post(url), token).content(body("Java developer. " + "x".repeat(19_984))))
			.andExpect(status().isOk());
	}

	@Test
	void requiresAToken() throws Exception {
		mockMvc.perform(get(analysisUrl(UUID.randomUUID().toString())))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post(analysisUrl(UUID.randomUUID().toString())).contentType(MediaType.APPLICATION_JSON)
			.content(body(POSTING))).andExpect(status().isUnauthorized());
	}

	@Test
	void aUserCannotReadOrAnalyzeAnotherUsersApplication() throws Exception {
		String owner = registerUser();
		String applicationId = createApplication(owner);
		mockMvc.perform(authorized(post(analysisUrl(applicationId)), owner).content(body(POSTING)))
			.andExpect(status().isOk());
		clearInvocations(chatClient);

		String intruder = registerUser();
		mockMvc.perform(authorized(get(analysisUrl(applicationId)), intruder))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
		mockMvc.perform(authorized(post(analysisUrl(applicationId)), intruder)
			.content(body("Intruder text that tries to overwrite the analysis.")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));

		verify(chatClient, never()).complete(any());
		mockMvc.perform(authorized(get(analysisUrl(applicationId)), owner))
			.andExpect(jsonPath("$.postingText").value(POSTING.strip()));
	}

	/**
	 * Twenty-five different postings sent at the same moment by one user: exactly twenty reach
	 * the model and five get 429. Check and increment are one SQL statement, so no two
	 * requests can both take the last call. Afterwards a cached result is still served.
	 */
	@Test
	void enforcesTheDailyLimitEvenUnderSimultaneousRequests() throws Exception {
		String token = registerUser();
		String applicationId = createApplication(token);
		clearInvocations(chatClient);

		int requests = 25;
		CountDownLatch start = new CountDownLatch(1);
		List<Callable<Integer>> calls = new ArrayList<>();
		for (int i = 0; i < requests; i++) {
			String text = POSTING + " Reference " + i;
			calls.add(() -> {
				start.await();
				return mockMvc.perform(authorized(post(analysisUrl(applicationId)), token).content(body(text)))
					.andReturn()
					.getResponse()
					.getStatus();
			});
		}
		List<Integer> statuses = new ArrayList<>();
		ExecutorService pool = Executors.newFixedThreadPool(requests);
		try {
			List<Future<Integer>> futures = new ArrayList<>();
			for (Callable<Integer> call : calls) {
				futures.add(pool.submit(call));
			}
			start.countDown();
			for (Future<Integer> future : futures) {
				statuses.add(future.get());
			}
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(statuses).filteredOn(code -> code == 200).hasSize(20);
		assertThat(statuses).filteredOn(code -> code == 429).hasSize(5);
		verify(chatClient, times(20)).complete(any());

		mockMvc
			.perform(authorized(post(analysisUrl(applicationId)), token)
				.content(body("A brand new posting that was never analyzed before.")))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("AI_DAILY_LIMIT_REACHED"));
		// The cache costs no AI call, so it still works when the limit is reached.
		// The application keeps the last stored analysis; sending its text again is a cache hit.
		String analyzedText = JsonPath.read(mockMvc.perform(authorized(get(analysisUrl(applicationId)), token))
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.postingText");
		mockMvc.perform(authorized(post(analysisUrl(applicationId)), token).content(body(analyzedText)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.cached").value(true));
	}

	@Test
	void theDailyCounterStopsAtTheLimitAndStartsAgainTheNextDay() throws Exception {
		String token = registerUser();
		UUID userId = UUID.fromString(JsonPath.read(mockMvc.perform(authorized(get("/api/users/me"), token))
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.id"));
		LocalDate day = LocalDate.of(2026, 11, 20);

		assertThat(increment(userId, day, 2)).isEqualTo(1);
		assertThat(increment(userId, day, 2)).isEqualTo(1);
		assertThat(increment(userId, day, 2)).isZero();
		assertThat(increment(userId, day.plusDays(1), 2)).isEqualTo(1);
		assertThat(aiUsage.findByUserIdAndUsageDate(userId, day)).get().extracting(AiUsage::getCalls).isEqualTo(2);
	}

	private int increment(UUID userId, LocalDate day, int limit) {
		return transaction.execute(status -> aiUsage.incrementIfBelowLimit(userId, day, limit));
	}

	private String registerUser() throws Exception {
		String body = """
				{"email": "user-%s@example.com", "password": "s3cret-password", "fullName": "Test User"}
				""".formatted(UUID.randomUUID());
		String response = mockMvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(response, "$.accessToken");
	}

	private String createApplication(String token) throws Exception {
		String response = mockMvc
			.perform(authorized(post("/api/applications"), token)
				.content("{\"company\": \"Acme\", \"roleTitle\": \"Backend Developer\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	private static String analysisUrl(String applicationId) {
		return "/api/applications/" + applicationId + "/analysis";
	}

	private static String body(String postingText) {
		return JSON.writeValueAsString(Map.of("postingText", postingText));
	}

	private static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, String token) {
		return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
	}

}
