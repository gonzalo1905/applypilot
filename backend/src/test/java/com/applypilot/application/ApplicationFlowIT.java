package com.applypilot.application;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.applypilot.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
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

/**
 * Applications API against a real PostgreSQL (Testcontainers). Each test registers its own
 * users, so tests do not depend on each other. Requires Docker to be running.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ApplicationFlowIT {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void createsReadsUpdatesAndDeletesAnApplication() throws Exception {
		String token = registerUser();

		String id = JsonPath.read(mockMvc
			.perform(authorized(post("/api/applications"), token).content("""
					{"company": "  Acme  ", "roleTitle": "Backend Developer", "jobUrl": "https://acme.example/jobs/1",
					 "location": "Lima", "workMode": "HYBRID", "salaryNote": "USD 3k", "notes": "Referred by Ana"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.company").value("Acme"))
			.andExpect(jsonPath("$.status").value("SAVED"))
			.andExpect(jsonPath("$.position").value(0))
			.andExpect(jsonPath("$.workMode").value("HYBRID"))
			.andExpect(jsonPath("$.appliedAt").doesNotExist())
			.andExpect(jsonPath("$.createdAt").isNotEmpty())
			.andExpect(jsonPath("$.userId").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.id");

		mockMvc.perform(authorized(get("/api/applications/" + id), token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.roleTitle").value("Backend Developer"));

		mockMvc
			.perform(authorized(put("/api/applications/" + id), token).content("""
					{"company": "Acme Corp", "roleTitle": "Senior Backend Developer", "jobUrl": "",
					 "appliedAt": "2026-11-02"}
					"""))
			.andExpect(status().isBadRequest());

		mockMvc
			.perform(authorized(put("/api/applications/" + id), token).content("""
					{"company": "Acme Corp", "roleTitle": "Senior Backend Developer", "appliedAt": "2026-11-02"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.company").value("Acme Corp"))
			.andExpect(jsonPath("$.appliedAt").value("2026-11-02"))
			.andExpect(jsonPath("$.location").doesNotExist())
			.andExpect(jsonPath("$.status").value("SAVED"));

		mockMvc.perform(authorized(delete("/api/applications/" + id), token)).andExpect(status().isNoContent());

		mockMvc.perform(authorized(get("/api/applications/" + id), token))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
		mockMvc.perform(authorized(get("/api/applications"), token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void movesCardsWithinAndBetweenColumnsKeepingPositionsContiguous() throws Exception {
		String token = registerUser();
		String a = createApplication(token, "A", "SAVED");
		String b = createApplication(token, "B", "SAVED");
		String c = createApplication(token, "C", "SAVED");
		String d = createApplication(token, "D", "INTERVIEW");

		// Reorder inside the column: C goes first -> C, A, B
		mockMvc.perform(authorized(patch("/api/applications/" + c + "/move"), token).content(moveBody("SAVED", 0)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.position").value(0));
		mockMvc.perform(authorized(get("/api/applications"), token))
			.andExpect(jsonPath("$[?(@.status == 'SAVED')].company", contains("C", "A", "B")))
			.andExpect(jsonPath("$[?(@.status == 'SAVED')].position", contains(0, 1, 2)));

		// Move A to INTERVIEW before D -> SAVED: C, B   INTERVIEW: A, D
		mockMvc.perform(authorized(patch("/api/applications/" + a + "/move"), token).content(moveBody("INTERVIEW", 0)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("INTERVIEW"))
			.andExpect(jsonPath("$.position").value(0));
		mockMvc.perform(authorized(get("/api/applications"), token))
			.andExpect(jsonPath("$[?(@.status == 'SAVED')].company", contains("C", "B")))
			.andExpect(jsonPath("$[?(@.status == 'SAVED')].position", contains(0, 1)))
			.andExpect(jsonPath("$[?(@.status == 'INTERVIEW')].company", contains("A", "D")))
			.andExpect(jsonPath("$[?(@.status == 'INTERVIEW')].position", contains(0, 1)));

		// A position past the end means "last"; reaching APPLIED stamps today's date.
		mockMvc.perform(authorized(patch("/api/applications/" + b + "/move"), token).content(moveBody("APPLIED", 99)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.position").value(0))
			.andExpect(jsonPath("$.appliedAt").value(LocalDate.now().toString()));

		// Deleting a card closes the gap it leaves.
		mockMvc.perform(authorized(delete("/api/applications/" + a), token)).andExpect(status().isNoContent());
		mockMvc.perform(authorized(get("/api/applications/" + d), token))
			.andExpect(jsonPath("$.position").value(0));
	}

	@Test
	void aUserCannotSeeOrChangeAnotherUsersApplications() throws Exception {
		String owner = registerUser();
		String intruder = registerUser();
		String id = createApplication(owner, "Private Co", "SAVED");

		mockMvc.perform(authorized(get("/api/applications"), intruder))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(0)));
		mockMvc.perform(authorized(get("/api/applications/" + id), intruder))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
		mockMvc
			.perform(authorized(put("/api/applications/" + id), intruder).content("""
					{"company": "Hacked", "roleTitle": "Hacked"}
					"""))
			.andExpect(status().isNotFound());
		mockMvc.perform(authorized(patch("/api/applications/" + id + "/move"), intruder).content(moveBody("OFFER", 0)))
			.andExpect(status().isNotFound());
		mockMvc.perform(authorized(delete("/api/applications/" + id), intruder)).andExpect(status().isNotFound());

		// The owner's card is untouched by all of the above.
		mockMvc.perform(authorized(get("/api/applications/" + id), owner))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.company").value("Private Co"))
			.andExpect(jsonPath("$.status").value("SAVED"));
	}

	@Test
	void rejectsInvalidInput() throws Exception {
		String token = registerUser();

		mockMvc
			.perform(authorized(post("/api/applications"), token).content("""
					{"company": "", "roleTitle": "Dev", "jobUrl": "javascript:alert(1)"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors[*].field").value(hasItem("company")))
			.andExpect(jsonPath("$.fieldErrors[*].field").value(hasItem("jobUrl")));

		mockMvc
			.perform(authorized(post("/api/applications"), token).content("""
					{"company": "Acme", "roleTitle": "Dev", "status": "HIRED"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

		String id = createApplication(token, "Acme", "SAVED");
		mockMvc.perform(authorized(patch("/api/applications/" + id + "/move"), token).content(moveBody("SAVED", -1)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field").value(hasItem("position")));

		mockMvc.perform(authorized(get("/api/applications/not-a-uuid"), token))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
	}

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/applications"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		mockMvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
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

	private String createApplication(String token, String company, String status) throws Exception {
		String body = """
				{"company": "%s", "roleTitle": "Developer", "status": "%s"}
				""".formatted(company, status);
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

	private static String moveBody(String status, int position) {
		return """
				{"status": "%s", "position": %d}
				""".formatted(status, position);
	}

}
