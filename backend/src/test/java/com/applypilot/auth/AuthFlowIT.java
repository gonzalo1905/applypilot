package com.applypilot.auth;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.applypilot.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Exercises the whole stack (security filters, controllers, services, Flyway schema) against
 * a real PostgreSQL started by Testcontainers. Requires Docker to be running.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthFlowIT {

	private static final String PASSWORD = "s3cret-password";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void registerThenLoginThenFetchOwnProfile() throws Exception {
		String email = uniqueEmail();

		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(registerBody(email, PASSWORD, "Ada Lovelace")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.user.email").value(email))
			.andExpect(jsonPath("$.user.fullName").value("Ada Lovelace"))
			.andExpect(content().string(not(Matchers.containsString("password"))));

		MvcResult login = mockMvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(loginBody(email, PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.expiresIn").isNumber())
			.andReturn();
		String token = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");

		mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").isNotEmpty())
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.fullName").value("Ada Lovelace"))
			.andExpect(jsonPath("$.createdAt").isNotEmpty())
			.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void profileRequiresAToken() throws Exception {
		mockMvc.perform(get("/api/users/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andExpect(jsonPath("$.path").value("/api/users/me"));
	}

	@Test
	void profileRejectsAForgedToken() throws Exception {
		mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void registerRejectsDuplicateEmail() throws Exception {
		String email = uniqueEmail();
		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(registerBody(email, PASSWORD, "Ada Lovelace")))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(registerBody(email.toUpperCase(), PASSWORD, "Another Ada")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
	}

	@Test
	void registerReportsValidationErrorsPerField() throws Exception {
		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(registerBody("not-an-email", "short", "")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors[*].field").value(hasItem("email")))
			.andExpect(jsonPath("$.fieldErrors[*].field").value(hasItem("password")))
			.andExpect(jsonPath("$.fieldErrors[*].field").value(hasItem("fullName")));
	}

	@Test
	void loginRejectsWrongPassword() throws Exception {
		String email = uniqueEmail();
		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(registerBody(email, PASSWORD, "Ada Lovelace")))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(loginBody(email, "wrong-password")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
			.andExpect(jsonPath("$.message").value("Invalid email or password"));
	}

	private static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

	private static String registerBody(String email, String password, String fullName) {
		return """
				{"email": "%s", "password": "%s", "fullName": "%s"}
				""".formatted(email, password, fullName);
	}

	private static String loginBody(String email, String password) {
		return """
				{"email": "%s", "password": "%s"}
				""".formatted(email, password);
	}

}
