package com.applypilot.cv;

import static org.assertj.core.api.Assertions.assertThat;

import com.applypilot.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * Uploads over real HTTP to the embedded Tomcat. The other integration tests use MockMvc,
 * which never goes through the servlet container, so they cannot prove that multipart
 * parsing works or that the container's own size limit answers with our error JSON.
 * Requires Docker to be running.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		properties = "app.cv.storage-dir=target/test-storage/cv-http")
@Import(TestcontainersConfiguration.class)
class CvUploadHttpIT {

	private static final String BOUNDARY = "----applypilot-test-boundary";

	@LocalServerPort
	private int port;

	private final HttpClient http = HttpClient.newHttpClient();

	@Test
	void uploadsAndDownloadsAPdfThroughTheRealServer() throws Exception {
		String token = registerUser();
		byte[] pdf = TestPdfs.withText("Uploaded over real HTTP");

		HttpResponse<String> upload = upload(token, "cv.pdf", pdf);

		assertThat(upload.statusCode()).isEqualTo(200);
		assertThat((String) JsonPath.read(upload.body(), "$.fileName")).isEqualTo("cv.pdf");
		assertThat((Boolean) JsonPath.read(upload.body(), "$.textExtracted")).isTrue();

		HttpResponse<byte[]> download = http.send(
				HttpRequest.newBuilder(uri("/api/users/me/cv/file")).header("Authorization", "Bearer " + token).build(),
				HttpResponse.BodyHandlers.ofByteArray());

		assertThat(download.statusCode()).isEqualTo(200);
		assertThat(download.headers().firstValue("Content-Type")).contains("application/pdf");
		assertThat(download.headers().firstValue("Content-Disposition").orElse("")).startsWith("attachment");
		assertThat(download.body()).isEqualTo(pdf);
	}

	@Test
	void theServerRejectsAFileOverTheLimitWithTheSharedErrorFormat() throws Exception {
		String token = registerUser();
		byte[] tooLarge = new byte[5 * 1024 * 1024 + 1024];
		System.arraycopy("%PDF-".getBytes(StandardCharsets.US_ASCII), 0, tooLarge, 0, 5);

		HttpResponse<String> upload = upload(token, "huge.pdf", tooLarge);

		assertThat(upload.statusCode()).isEqualTo(413);
		assertThat((String) JsonPath.read(upload.body(), "$.code")).isEqualTo("FILE_TOO_LARGE");
	}

	@Test
	void aMultipartRequestWithoutAFileIsAClientError() throws Exception {
		String token = registerUser();
		HttpRequest request = HttpRequest.newBuilder(uri("/api/users/me/cv"))
			.header("Authorization", "Bearer " + token)
			.header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
			.POST(HttpRequest.BodyPublishers.ofString("--" + BOUNDARY + "--\r\n"))
			.build();

		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat((String) JsonPath.read(response.body(), "$.path")).isEqualTo("/api/users/me/cv");
	}

	private HttpResponse<String> upload(String token, String fileName, byte[] content) throws Exception {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write(("--" + BOUNDARY + "\r\n" + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName
				+ "\"\r\n" + "Content-Type: application/pdf\r\n\r\n")
			.getBytes(StandardCharsets.UTF_8));
		body.write(content);
		body.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));

		HttpRequest request = HttpRequest.newBuilder(uri("/api/users/me/cv"))
			.header("Authorization", "Bearer " + token)
			.header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
			.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
			.build();
		return http.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private String registerUser() throws Exception {
		String json = """
				{"email": "user-%s@example.com", "password": "s3cret-password", "fullName": "Test User"}
				""".formatted(UUID.randomUUID());
		HttpRequest request = HttpRequest.newBuilder(uri("/api/auth/register"))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(json))
			.build();
		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(201);
		return JsonPath.read(response.body(), "$.accessToken");
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

}
