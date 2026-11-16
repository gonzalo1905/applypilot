package com.applypilot.cv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.applypilot.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * CV upload API against a real PostgreSQL (Testcontainers) and a throwaway storage folder
 * under {@code target/}. Requires Docker to be running.
 */
@SpringBootTest(properties = "app.cv.storage-dir=target/test-storage")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CvFlowIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CvRepository cvRepository;

	@Test
	void uploadsReplacesDownloadsAndDeletesACv() throws Exception {
		String token = registerUser();
		byte[] first = TestPdfs.withText("Backend developer, Java and Spring Boot");
		byte[] second = TestPdfs.withText("Full-stack developer, Java and Angular");

		mockMvc.perform(get("/api/users/me/cv").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CV_NOT_FOUND"));

		mockMvc.perform(upload(token, pdfPart("cv.pdf", first)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.fileName").value("cv.pdf"))
			.andExpect(jsonPath("$.sizeBytes").value(first.length))
			.andExpect(jsonPath("$.textExtracted").value(true))
			.andExpect(jsonPath("$.extractedText").doesNotExist())
			.andExpect(jsonPath("$.storageKey").doesNotExist());

		String cvId = JsonPath.read(mockMvc.perform(upload(token, pdfPart("cv-v2.pdf", second)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.fileName").value("cv-v2.pdf"))
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.id");

		// The text the AI features will read is stored with the record.
		assertThat(cvRepository.findById(UUID.fromString(cvId)))
			.get()
			.extracting(Cv::getExtractedText)
			.isEqualTo("Full-stack developer, Java and Angular");

		mockMvc.perform(get("/api/users/me/cv/file").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_PDF))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("cv-v2.pdf")))
			.andExpect(content().bytes(second));

		mockMvc.perform(delete("/api/users/me/cv").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/users/me/cv/file").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isNotFound());
	}

	@Test
	void eachUserOnlyReachesTheirOwnCv() throws Exception {
		String owner = registerUser();
		String other = registerUser();
		mockMvc.perform(upload(owner, pdfPart("owner.pdf", TestPdfs.withText("Owner CV")))).andExpect(status().isOk());

		mockMvc.perform(get("/api/users/me/cv").header(HttpHeaders.AUTHORIZATION, "Bearer " + other))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/users/me/cv/file").header(HttpHeaders.AUTHORIZATION, "Bearer " + other))
			.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/users/me/cv").header(HttpHeaders.AUTHORIZATION, "Bearer " + other))
			.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/users/me/cv").header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.fileName").value("owner.pdf"));
	}

	@Test
	void rejectsFilesThatAreNotValidPdfs() throws Exception {
		String token = registerUser();
		byte[] text = "just a text file".getBytes(StandardCharsets.UTF_8);

		// Wrong declared type.
		mockMvc.perform(upload(token, new MockMultipartFile("file", "notes.txt", "text/plain", text)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value("Only PDF files are accepted"));

		// Declared as PDF and named .pdf, but the content is not a PDF.
		mockMvc.perform(upload(token, pdfPart("fake.pdf", text)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Only PDF files are accepted"));

		// Carries the PDF signature but cannot be parsed.
		mockMvc.perform(upload(token, pdfPart("broken.pdf", "%PDF-1.7 broken".getBytes(StandardCharsets.US_ASCII))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("The file is not a readable PDF"));

		mockMvc.perform(upload(token, pdfPart("empty.pdf", new byte[0])))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("The file is empty"));

		// Larger than the 5 MB limit.
		byte[] tooLarge = new byte[5 * 1024 * 1024 + 1];
		System.arraycopy("%PDF-".getBytes(StandardCharsets.US_ASCII), 0, tooLarge, 0, 5);
		mockMvc.perform(upload(token, pdfPart("huge.pdf", tooLarge)))
			.andExpect(status().is(413))
			.andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));

		// Nothing was stored by any of the rejected uploads.
		mockMvc.perform(get("/api/users/me/cv").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isNotFound());
	}

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(multipart("/api/users/me/cv").file(pdfPart("cv.pdf", TestPdfs.withText("x"))))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/users/me/cv/file")).andExpect(status().isUnauthorized());
	}

	private static RequestBuilder upload(String token, MockMultipartFile file) {
		return multipart("/api/users/me/cv").file(file).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}

	private static MockMultipartFile pdfPart(String fileName, byte[] content) {
		return new MockMultipartFile("file", fileName, "application/pdf", content);
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

}
