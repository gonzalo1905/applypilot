package com.applypilot.insights;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.applypilot.TestcontainersConfiguration;
import com.applypilot.agent.AgentRun;
import com.applypilot.agent.AgentRunRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Board insights against a real PostgreSQL (Testcontainers). Requires Docker to be running. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InsightsFlowIT {

	private static final Instant T0 = Instant.parse("2026-11-20T10:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AgentRunRepository runs;

	@Test
	void returnsTheLatestMatchScoreOfEachOwnApplicationAndTheAiAllowance() throws Exception {
		String token = registerUser();
		UUID userId = userId(token);
		UUID rematched = UUID.fromString(createApplication(token, "Northwind"));
		UUID failed = UUID.fromString(createApplication(token, "CloudNest"));
		UUID neverMatched = UUID.fromString(createApplication(token, "Brightlane"));

		saveRun(rematched, userId, T0, 58);
		saveRun(rematched, userId, T0.plusSeconds(60), 83);
		// A later run that failed before matching does not hide the earlier score.
		saveRun(failed, userId, T0, 67);
		saveRun(failed, userId, T0.plusSeconds(60), null);

		mockMvc.perform(authorized(get("/api/insights"), token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.matchScores", aMapWithSize(2)))
			.andExpect(jsonPath("$.matchScores['" + rematched + "']").value(83))
			.andExpect(jsonPath("$.matchScores['" + failed + "']").value(67))
			.andExpect(jsonPath("$.matchScores['" + neverMatched + "']").doesNotExist())
			.andExpect(jsonPath("$.aiCallsLeft").value(20))
			.andExpect(jsonPath("$.aiDailyLimit").value(20));
	}

	@Test
	void neverShowsAnotherUsersScores() throws Exception {
		String owner = registerUser();
		UUID application = UUID.fromString(createApplication(owner, "Northwind"));
		saveRun(application, userId(owner), T0, 83);

		mockMvc.perform(authorized(get("/api/insights"), registerUser()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.matchScores", aMapWithSize(0)));
	}

	@Test
	void requiresAToken() throws Exception {
		mockMvc.perform(get("/api/insights")).andExpect(status().isUnauthorized());
	}

	private void saveRun(UUID applicationId, UUID userId, Instant createdAt, Integer score) {
		AgentRun run = new AgentRun(applicationId, userId, createdAt);
		if (score != null) {
			run.matched("""
					{"score": %d, "matchedSkills": [], "missingSkills": [], "summary": "s", "rejectedEvidence": 0}
					""".formatted(score));
		}
		runs.save(run);
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

	private UUID userId(String token) throws Exception {
		String response = mockMvc.perform(authorized(get("/api/users/me"), token))
			.andReturn()
			.getResponse()
			.getContentAsString();
		return UUID.fromString(JsonPath.read(response, "$.id"));
	}

	private String createApplication(String token, String company) throws Exception {
		String body = """
				{"company": "%s", "roleTitle": "Developer"}
				""".formatted(company);
		String response = mockMvc.perform(authorized(post("/api/applications"), token).content(body))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	private static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, String token) {
		return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
	}

}
