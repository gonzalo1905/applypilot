package com.applypilot.demo;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.applypilot.TestcontainersConfiguration;
import com.applypilot.auth.DemoAccount;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The public demo's settings: a seeded demo account entered without a password, registration
 * closed, and the health endpoint open to the hosting platform. No AI key in tests, so the
 * demo postings are analyzed by the keyword fallback. Requires Docker.
 */
@SpringBootTest(properties = { "app.access.demo-enabled=true", "app.access.registration-enabled=false" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DemoFlowIT {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void tryTheDemoLogsIntoASeededAccountWithFictitiousData() throws Exception {
		String body = mockMvc.perform(post("/api/auth/demo"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.email").value(DemoAccount.EMAIL))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String token = JsonPath.read(body, "$.accessToken");

		mockMvc.perform(get("/api/applications").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(5)));
		mockMvc.perform(get("/api/users/me/cv").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.fileName").value(DemoData.CV_FILE_NAME));
	}

	@Test
	void registrationIsClosedOnTheDemo() throws Exception {
		mockMvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"new@example.com\", \"password\": \"s3cret-password\", \"fullName\": \"New\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("REGISTRATION_CLOSED"));
	}

	@Test
	void theHealthEndpointIsPublicAndRevealsNothingElse() throws Exception {
		mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.components").doesNotExist());
		mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
	}

}
