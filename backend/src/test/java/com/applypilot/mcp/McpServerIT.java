package com.applypilot.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.applypilot.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * The MCP server end to end, over real HTTP with the MCP Java client, as Claude Desktop or
 * Claude Code would use it. Requires Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class McpServerIT {

	private static final HttpClient HTTP = HttpClient.newHttpClient();

	@LocalServerPort
	private int port;

	@Test
	void aPersonalAccessTokenListsOnlyTheOwnersApplications() throws Exception {
		String owner = register();
		createApplication(owner, "Northwind Payments");
		String token = createToken(owner);
		String intruder = register();
		createApplication(intruder, "Someone Else Inc");

		try (McpSyncClient client = client(token)) {
			client.initialize();
			assertThat(client.listTools().tools()).extracting(McpSchema.Tool::name)
				.containsExactlyInAnyOrder("list_applications", "get_match");

			McpSchema.CallToolResult result = client
				.callTool(new McpSchema.CallToolRequest("list_applications", Map.of()));
			String text = ((McpSchema.TextContent) result.content().get(0)).text();
			assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
			assertThat(text).contains("Northwind Payments").doesNotContain("Someone Else Inc");
		}
	}

	@Test
	void requestsWithoutAValidPersonalAccessTokenAreRejected() throws Exception {
		String loginToken = register();
		HttpRequest.Builder initialize = HttpRequest.newBuilder(URI.create(url("/mcp")))
			.header("Content-Type", "application/json")
			.header("Accept", "application/json, text/event-stream")
			.POST(HttpRequest.BodyPublishers.ofString(
					"{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"));

		assertThat(send(initialize.copy()).statusCode()).isEqualTo(401);
		// A login token is not enough: only personal access tokens open the MCP endpoint.
		assertThat(send(initialize.copy().header("Authorization", "Bearer " + loginToken)).statusCode())
			.isEqualTo(401);
		assertThat(send(initialize.copy().header("Authorization", "Bearer ap_pat_invented")).statusCode())
			.isEqualTo(401);
	}

	@Test
	void aPersonalAccessTokenOpensNothingButTheMcpEndpoint() throws Exception {
		String token = createToken(register());

		HttpResponse<String> api = send(HttpRequest.newBuilder(URI.create(url("/api/applications")))
			.header("Authorization", "Bearer " + token));

		assertThat(api.statusCode()).isEqualTo(401);
	}

	@Test
	void aRevokedTokenStopsWorking() throws Exception {
		String owner = register();
		String token = createToken(owner);
		String tokenId = JsonPath.read(send(authorized(HttpRequest.newBuilder(URI.create(url("/api/users/me/tokens"))),
				owner).GET()).body(), "$[0].id");

		HttpResponse<String> revoke = send(authorized(
				HttpRequest.newBuilder(URI.create(url("/api/users/me/tokens/" + tokenId))), owner).DELETE());
		assertThat(revoke.statusCode()).isEqualTo(204);

		try (McpSyncClient client = client(token)) {
			assertThatThrownBy(client::initialize).isInstanceOf(RuntimeException.class);
		}
	}

	private McpSyncClient client(String personalAccessToken) {
		HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(url(""))
			.endpoint("/mcp")
			.requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer " + personalAccessToken))
			.build();
		return McpClient.sync(transport).requestTimeout(Duration.ofSeconds(20)).build();
	}

	private String register() throws Exception {
		String body = """
				{"email": "mcp-%s@example.com", "password": "s3cret-password", "fullName": "Test User"}
				""".formatted(UUID.randomUUID());
		HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(url("/api/auth/register")))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body)));
		assertThat(response.statusCode()).isEqualTo(201);
		return JsonPath.read(response.body(), "$.accessToken");
	}

	private void createApplication(String loginToken, String company) throws Exception {
		HttpResponse<String> response = send(authorized(HttpRequest.newBuilder(URI.create(url("/api/applications"))),
				loginToken)
			.POST(HttpRequest.BodyPublishers
				.ofString("{\"company\": \"" + company + "\", \"roleTitle\": \"Backend Developer\"}")));
		assertThat(response.statusCode()).isEqualTo(201);
	}

	private String createToken(String loginToken) throws Exception {
		HttpResponse<String> response = send(authorized(HttpRequest.newBuilder(URI.create(url("/api/users/me/tokens"))),
				loginToken)
			.POST(HttpRequest.BodyPublishers.ofString("{\"name\": \"Claude Desktop\"}")));
		assertThat(response.statusCode()).isEqualTo(201);
		String token = JsonPath.read(response.body(), "$.token");
		assertThat(token).startsWith("ap_pat_");
		return token;
	}

	private static HttpRequest.Builder authorized(HttpRequest.Builder request, String token) {
		return request.header("Authorization", "Bearer " + token).header("Content-Type", "application/json");
	}

	private static HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
		return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private String url(String path) {
		return "http://localhost:" + port + path;
	}

}
