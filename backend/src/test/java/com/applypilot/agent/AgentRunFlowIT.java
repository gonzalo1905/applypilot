package com.applypilot.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.applypilot.TestcontainersConfiguration;
import com.applypilot.analysis.AiUsage;
import com.applypilot.analysis.AiUsageRepository;
import com.applypilot.common.ai.ChatCompletion;
import com.applypilot.common.ai.ChatCompletionClient;
import com.applypilot.common.ai.ChatCompletionRequest;
import com.applypilot.common.ai.LlmUnavailableException;
import com.applypilot.cv.Cv;
import com.applypilot.cv.CvRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
 * Match runs against a real PostgreSQL, with the real background executor and a fake model
 * (a Mockito mock of {@link ChatCompletionClient} that answers by agent). Requires Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AgentRunFlowIT {

	private static final String CV_TEXT = """
			Ana Torres - Backend Developer
			Summary: Backend developer with 6 years of Java experience.
			Acme Payments (2021-2025): built REST APIs with Spring Boot and PostgreSQL.
			Deployed services with Docker on AWS.
			""";

	private static final String POSTING = """
			Acme is hiring a Senior Backend Developer (remote, Lima).
			Requirements: Java, Spring Boot, Kafka. Nice to have: Docker. English required.
			""";

	private static final String EXTRACTION = """
			{"company": "Acme", "roleTitle": "Backend Developer", "seniority": "SENIOR",
			 "requiredSkills": ["Java", "Spring Boot", "Kafka"], "niceToHaveSkills": ["Docker"],
			 "workMode": "REMOTE", "location": "Lima, Peru", "languages": ["English"],
			 "salary": null, "summary": "Backend role building REST APIs with Spring Boot."}
			""";

	private static final String MATCH = """
			{"skills": [
			  {"skill": "Java", "matched": true, "evidence": "6 years of Java experience"},
			  {"skill": "Spring Boot", "matched": true, "evidence": "REST APIs with Spring Boot"},
			  {"skill": "Kafka", "matched": false, "evidence": null},
			  {"skill": "Docker", "matched": true, "evidence": "Docker on AWS"}
			], "summary": "Good fit without Kafka."}
			""";

	private static final String APPROVAL = """
			{"approved": true, "fabricatedClaims": [], "missingRequirements": [], "toneIssues": [], "suggestions": []}
			""";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CvRepository cvs;

	@Autowired
	private AiUsageRepository aiUsage;

	@Autowired
	private TransactionTemplate transaction;

	@MockitoBean
	private ChatCompletionClient chatClient;

	@BeforeEach
	void modelAnswersByAgent() {
		when(chatClient.isConfigured()).thenReturn(true);
		when(chatClient.converse(any())).thenReturn(answer(MATCH));
		when(chatClient.complete(any())).thenAnswer(invocation -> {
			ChatCompletionRequest request = invocation.getArgument(0);
			if (request.systemPrompt().equals(WriterAgent.SYSTEM_PROMPT)) {
				return answer(JSON.writeValueAsString(new CoverLetterDraft("en", WriterAndReviewerTest.LETTER)));
			}
			if (request.systemPrompt().equals(ReviewerAgent.SYSTEM_PROMPT)) {
				return answer(APPROVAL);
			}
			return answer(EXTRACTION);
		});
	}

	@Test
	void runsTheWholeTeamInTheBackgroundAndRecordsEveryStep() throws Exception {
		User user = registerUserWithCv();
		String applicationId = createApplication(user.token());
		analyze(user.token(), applicationId);

		String runId = JsonPath.read(mockMvc.perform(authorized(post(runsUrl(applicationId)), user.token()))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.applicationId").value(applicationId))
			.andExpect(jsonPath("$.status").exists())
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.id");

		String run = waitForEnd(user.token(), applicationId);

		assertThat((String) JsonPath.read(run, "$.id")).isEqualTo(runId);
		assertThat((String) JsonPath.read(run, "$.status")).isEqualTo("SUCCEEDED");
		assertThat((Integer) JsonPath.read(run, "$.match.score")).isEqualTo(71);
		assertThat((List<String>) JsonPath.read(run, "$.match.missingSkills[*].skill")).containsExactly("Kafka");
		assertThat((String) JsonPath.read(run, "$.coverLetter")).startsWith("Dear Acme team");
		assertThat((String) JsonPath.read(run, "$.letterLanguage")).isEqualTo("en");
		assertThat((Boolean) JsonPath.read(run, "$.review.approved")).isTrue();
		assertThat((List<String>) JsonPath.read(run, "$.steps[*].agent")).containsExactly("EXTRACTOR", "MATCHER",
				"WRITER", "REVIEWER");
		assertThat((List<String>) JsonPath.read(run, "$.steps[*].source")).containsExactly("REUSED", "LLM", "LLM",
				"LLM");
		assertThat((List<Integer>) JsonPath.read(run, "$.steps[*].promptTokens")).containsExactly(null, 100, 100, 100);
		assertThat((String) JsonPath.read(run, "$.finishedAt")).isNotNull();
		// One analysis plus one call for each agent: every model call counts against the limit.
		assertThat(aiUsage.findByUserIdAndUsageDate(user.id(), LocalDate.now())).get()
			.extracting(AiUsage::getCalls)
			.isEqualTo(4);
	}

	@Test
	void postingTextInTheRequestIsAnalyzedByTheExtractorFirst() throws Exception {
		User user = registerUserWithCv();
		String applicationId = createApplication(user.token());

		mockMvc.perform(authorized(post(runsUrl(applicationId)), user.token())
			.content(JSON.writeValueAsString(Map.of("postingText", POSTING)))).andExpect(status().isAccepted());

		String run = waitForEnd(user.token(), applicationId);
		assertThat((String) JsonPath.read(run, "$.status")).isEqualTo("SUCCEEDED");
		assertThat((String) JsonPath.read(run, "$.steps[0].source")).isEqualTo("LLM");
		mockMvc.perform(authorized(get("/api/applications/" + applicationId + "/analysis"), user.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.requiredSkills", contains("Java", "Spring Boot", "Kafka")));
	}

	@Test
	void withoutTheWriterTheRunIsPartialButKeepsTheMatch() throws Exception {
		doAnswer(invocation -> {
			ChatCompletionRequest request = invocation.getArgument(0);
			if (request.systemPrompt().equals(WriterAgent.SYSTEM_PROMPT)) {
				throw new LlmUnavailableException("timeout", null);
			}
			return answer(EXTRACTION);
		}).when(chatClient).complete(any());
		User user = registerUserWithCv();
		String applicationId = createApplication(user.token());
		analyze(user.token(), applicationId);

		mockMvc.perform(authorized(post(runsUrl(applicationId)), user.token())).andExpect(status().isAccepted());

		String run = waitForEnd(user.token(), applicationId);
		assertThat((String) JsonPath.read(run, "$.status")).isEqualTo("PARTIAL");
		assertThat((Integer) JsonPath.read(run, "$.match.score")).isEqualTo(71);
		assertThat((Object) JsonPath.read(run, "$.coverLetter")).isNull();
		assertThat((List<String>) JsonPath.read(run, "$.steps[*].source")).containsExactly("REUSED", "LLM", "SKIPPED",
				"SKIPPED");
		assertThat((String) JsonPath.read(run, "$.steps[2].reason")).isEqualTo("PROVIDER_ERROR");
	}

	@Test
	void aSecondStartWhileRunningReturnsTheSameRun() throws Exception {
		CountDownLatch release = new CountDownLatch(1);
		doAnswer(invocation -> {
			release.await(10, TimeUnit.SECONDS);
			return answer(MATCH);
		}).when(chatClient).converse(any());
		User user = registerUserWithCv();
		String applicationId = createApplication(user.token());
		analyze(user.token(), applicationId);
		try {
			String first = JsonPath.read(mockMvc.perform(authorized(post(runsUrl(applicationId)), user.token()))
				.andReturn()
				.getResponse()
				.getContentAsString(), "$.id");
			String second = JsonPath.read(mockMvc.perform(authorized(post(runsUrl(applicationId)), user.token()))
				.andExpect(status().isAccepted())
				.andReturn()
				.getResponse()
				.getContentAsString(), "$.id");
			assertThat(second).isEqualTo(first);
		}
		finally {
			release.countDown();
		}
		assertThat((String) JsonPath.read(waitForEnd(user.token(), applicationId), "$.status")).isEqualTo("SUCCEEDED");
	}

	@Test
	void checksThePreconditionsBeforeQueueingAnything() throws Exception {
		String token = registerUser().token();
		String applicationId = createApplication(token);

		mockMvc.perform(authorized(post(runsUrl(applicationId)), token))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CV_REQUIRED"));

		User withCv = registerUserWithCv();
		String other = createApplication(withCv.token());
		mockMvc.perform(authorized(post(runsUrl(other)), withCv.token()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ANALYSIS_REQUIRED"));
		mockMvc.perform(authorized(post(runsUrl(other)), withCv.token())
			.content(JSON.writeValueAsString(Map.of("postingText", "too short"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("postingText"));
		mockMvc.perform(authorized(get(runsUrl(other) + "/latest"), withCv.token()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("AGENT_RUN_NOT_FOUND"));
		verify(chatClient, never()).converse(any());
	}

	@Test
	void answersTooManyRequestsWhenNoAiCallIsLeftToday() throws Exception {
		User user = registerUserWithCv();
		String applicationId = createApplication(user.token());
		analyze(user.token(), applicationId);
		transaction.executeWithoutResult(status -> {
			while (aiUsage.incrementIfBelowLimit(user.id(), LocalDate.now(), 20) == 1) {
				// use up the day's calls
			}
		});

		mockMvc.perform(authorized(post(runsUrl(applicationId)), user.token()))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("AI_DAILY_LIMIT_REACHED"));
	}

	@Test
	void aUserCannotStartOrReadAnotherUsersRun() throws Exception {
		User owner = registerUserWithCv();
		String applicationId = createApplication(owner.token());
		analyze(owner.token(), applicationId);
		mockMvc.perform(authorized(post(runsUrl(applicationId)), owner.token())).andExpect(status().isAccepted());
		waitForEnd(owner.token(), applicationId);
		clearInvocations(chatClient);

		User intruder = registerUserWithCv();
		mockMvc.perform(authorized(get(runsUrl(applicationId) + "/latest"), intruder.token()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
		mockMvc.perform(authorized(post(runsUrl(applicationId)), intruder.token()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
		verify(chatClient, never()).converse(any());
		verify(chatClient, never()).complete(any());
	}

	@Test
	void requiresAToken() throws Exception {
		mockMvc.perform(post(runsUrl(UUID.randomUUID().toString()))).andExpect(status().isUnauthorized());
		mockMvc.perform(get(runsUrl(UUID.randomUUID().toString()) + "/latest")).andExpect(status().isUnauthorized());
	}

	private String waitForEnd(String token, String applicationId) throws Exception {
		for (int attempt = 0; attempt < 200; attempt++) {
			String body = mockMvc.perform(authorized(get(runsUrl(applicationId) + "/latest"), token))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
			String status = JsonPath.read(body, "$.status");
			if (!status.equals("PENDING") && !status.equals("RUNNING")) {
				return body;
			}
			Thread.sleep(50);
		}
		throw new AssertionError("The run did not finish within 10 seconds");
	}

	private User registerUserWithCv() throws Exception {
		User user = registerUser();
		transaction.executeWithoutResult(status -> {
			Cv cv = new Cv(user.id());
			cv.replaceFile("cv.pdf", "application/pdf", 1000, "test/" + UUID.randomUUID() + ".pdf", CV_TEXT,
					Instant.now());
			cvs.save(cv);
		});
		return user;
	}

	private User registerUser() throws Exception {
		String body = """
				{"email": "user-%s@example.com", "password": "s3cret-password", "fullName": "Test User"}
				""".formatted(UUID.randomUUID());
		String response = mockMvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String token = JsonPath.read(response, "$.accessToken");
		String me = mockMvc.perform(authorized(get("/api/users/me"), token))
			.andReturn()
			.getResponse()
			.getContentAsString();
		return new User(token, UUID.fromString(JsonPath.read(me, "$.id")));
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

	private void analyze(String token, String applicationId) throws Exception {
		mockMvc
			.perform(authorized(post("/api/applications/" + applicationId + "/analysis"), token)
				.content(JSON.writeValueAsString(Map.of("postingText", POSTING))))
			.andExpect(status().isOk());
	}

	private static ChatCompletion answer(String content) {
		return new ChatCompletion(content, "fake-model", 100, 20);
	}

	private static String runsUrl(String applicationId) {
		return "/api/applications/" + applicationId + "/agent-runs";
	}

	private static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, String token) {
		return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
	}

	private record User(String token, UUID id) {
	}

}
