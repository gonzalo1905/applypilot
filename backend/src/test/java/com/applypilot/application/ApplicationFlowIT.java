package com.applypilot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

		// A form sends "" for fields the user left empty: they are stored as "no value".
		mockMvc
			.perform(authorized(put("/api/applications/" + id), token).content("""
					{"company": "Acme Corp", "roleTitle": "Senior Backend Developer", "jobUrl": "", "location": "",
					 "notes": "", "appliedAt": "2026-11-02"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.company").value("Acme Corp"))
			.andExpect(jsonPath("$.appliedAt").value("2026-11-02"))
			.andExpect(jsonPath("$.jobUrl").doesNotExist())
			.andExpect(jsonPath("$.location").doesNotExist())
			.andExpect(jsonPath("$.notes").doesNotExist())
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
		LocalDate before = LocalDate.now();
		mockMvc.perform(authorized(patch("/api/applications/" + b + "/move"), token).content(moveBody("APPLIED", 99)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.position").value(0))
			// Either date is right if the test happens to run across midnight.
			.andExpect(jsonPath("$.appliedAt", anyOf(is(before.toString()), is(LocalDate.now().toString()))));

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

		// A date PostgreSQL could not even store must be a 400, not a 500.
		mockMvc
			.perform(authorized(post("/api/applications"), token).content("""
					{"company": "Acme", "roleTitle": "Dev", "appliedAt": "+5874898-01-01"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void removesCharactersTheDatabaseCannotStore() throws Exception {
		String token = registerUser();

		// \\u0000 in the JSON is the NUL character, which PostgreSQL text columns reject.
		mockMvc
			.perform(authorized(post("/api/applications"), token).content("""
					{"company": "Ac\\u0000me", "roleTitle": "Dev", "notes": "\\u0000"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.company").value("Acme"))
			.andExpect(jsonPath("$.notes").doesNotExist());
	}

	@Test
	void simultaneousMovesNeverCorruptTheColumnOrFail() throws Exception {
		String token = registerUser();
		List<String> ids = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			ids.add(createApplication(token, "Card " + i, "SAVED"));
		}

		// Six threads keep sending cards to the top and to the bottom of the same column.
		List<Callable<Integer>> moves = new ArrayList<>();
		for (int round = 0; round < 4; round++) {
			for (int i = 0; i < ids.size(); i++) {
				String id = ids.get(i);
				int position = (i + round) % 2 == 0 ? 0 : 99;
				moves.add(() -> mockMvc
					.perform(authorized(patch("/api/applications/" + id + "/move"), token)
						.content(moveBody("SAVED", position)))
					.andReturn()
					.getResponse()
					.getStatus());
			}
		}
		ExecutorService pool = Executors.newFixedThreadPool(6);
		try {
			for (Future<Integer> result : pool.invokeAll(moves)) {
				assertThat(result.get()).isEqualTo(200);
			}
		}
		finally {
			pool.shutdownNow();
		}

		mockMvc.perform(authorized(get("/api/applications"), token))
			.andExpect(jsonPath("$", hasSize(6)))
			.andExpect(jsonPath("$[*].position", contains(0, 1, 2, 3, 4, 5)));
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
