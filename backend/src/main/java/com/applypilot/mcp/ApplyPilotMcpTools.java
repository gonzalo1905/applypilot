package com.applypilot.mcp;

import com.applypilot.agent.AgentRunService;
import com.applypilot.agent.MatchResult;
import com.applypilot.agent.dto.AgentRunResponse;
import com.applypilot.application.ApplicationService;
import com.applypilot.application.ApplicationStatus;
import com.applypilot.application.dto.ApplicationResponse;
import com.applypilot.common.error.ApiException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * The tools ApplyPilot offers over MCP: read-only views of the authenticated user's board and
 * match results. They call the same services as the REST API, with the user id taken from the
 * personal access token, so ownership checks are the same. Nothing here writes, and nothing
 * calls the language model, so an MCP client cannot spend the user's AI quota.
 *
 * <p>Results contain text from job postings and CVs. For the model of the MCP client that is
 * untrusted data, exactly as it is for our own agents.
 */
@Component
public class ApplyPilotMcpTools {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApplyPilotMcpTools.class);

	private final ApplicationService applications;

	private final AgentRunService runs;

	/**
	 * Lazy on purpose: Spring AI's tool resolver collects every tool provider while the chat
	 * model is created, and the agent services need that chat model. Without the lazy
	 * proxies the two would depend on each other at startup.
	 */
	public ApplyPilotMcpTools(@Lazy ApplicationService applications, @Lazy AgentRunService runs) {
		this.applications = applications;
		this.runs = runs;
	}

	@Tool(name = "list_applications",
			description = "Lists the user's job applications on their ApplyPilot board: company, role, column "
					+ "(SAVED, APPLIED, INTERVIEW, OFFER, REJECTED) and the date applied. Use the id with get_match.")
	public List<ApplicationSummary> listApplications(@ToolParam(required = false,
			description = "Only applications in this column: SAVED, APPLIED, INTERVIEW, OFFER or REJECTED") String status) {
		ApplicationStatus filter = parseStatus(status);
		return safely(() -> applications.list(McpUser.require())
			.stream()
			.filter(application -> filter == null || application.status() == filter)
			.map(ApplicationSummary::from)
			.toList());
	}

	@Tool(name = "get_match",
			description = "Returns the latest match of the user's CV with one application: score from 0 to 100, "
					+ "matched skills with a quote from the CV, missing skills, the cover letter and the "
					+ "reviewer's verdict. The application id comes from list_applications.")
	public MatchSummary getMatch(@ToolParam(description = "The application id") String applicationId) {
		UUID id;
		try {
			id = UUID.fromString(applicationId.strip());
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("applicationId must be an id returned by list_applications");
		}
		return safely(() -> MatchSummary.from(runs.latest(McpUser.require(), id)));
	}

	/**
	 * The tool's error message goes to the MCP client as is. Our API errors are written for
	 * users; anything else (a database error, for example) could reveal internals, so it is
	 * logged here and replaced by a generic message.
	 */
	private static <T> T safely(java.util.function.Supplier<T> call) {
		try {
			return call.get();
		}
		catch (ApiException ex) {
			throw new IllegalArgumentException(ex.getMessage());
		}
		catch (RuntimeException ex) {
			log.error("MCP tool failed: {}", ex.getClass().getName());
			throw new IllegalStateException("ApplyPilot could not answer right now. Try again later.");
		}
	}

	private static ApplicationStatus parseStatus(String status) {
		if (status == null || status.isBlank()) {
			return null;
		}
		try {
			return ApplicationStatus.valueOf(status.strip().toUpperCase(java.util.Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("status must be SAVED, APPLIED, INTERVIEW, OFFER or REJECTED");
		}
	}

	public record ApplicationSummary(UUID id, String company, String roleTitle, ApplicationStatus status,
			LocalDate appliedAt, String location) {

		static ApplicationSummary from(ApplicationResponse application) {
			return new ApplicationSummary(application.id(), application.company(), application.roleTitle(),
					application.status(), application.appliedAt(), application.location());
		}

	}

	/**
	 * @param status SUCCEEDED, PARTIAL (match without a letter), FAILED, or still running
	 */
	public record MatchSummary(String status, Integer score, List<MatchResult.MatchedSkill> matchedSkills,
			List<MatchResult.MissingSkill> missingSkills, String summary, String coverLetter,
			Boolean reviewerApproved) {

		static MatchSummary from(AgentRunResponse run) {
			MatchResult match = run.match();
			return new MatchSummary(run.status().name(), match == null ? null : match.score(),
					match == null ? List.of() : match.matchedSkills(), match == null ? List.of() : match.missingSkills(),
					match == null ? null : match.summary(), run.coverLetter(),
					run.review() == null ? null : run.review().approved());
		}

	}

}
