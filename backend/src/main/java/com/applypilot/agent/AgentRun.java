package com.applypilot.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One run of the agent team for one application. The results are stored as JSON text
 * written by the application ({@code matchResult}, {@code review}); the steps that produced
 * them are {@link AgentRunStep} rows.
 */
@Entity
@Table(name = "agent_runs")
public class AgentRun {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "application_id", nullable = false, updatable = false)
	private UUID applicationId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RunStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "current_agent", length = 20)
	private AgentName currentAgent;

	@Column(name = "match_result", columnDefinition = "text")
	private String matchResult;

	@Column(name = "cover_letter", columnDefinition = "text")
	private String coverLetter;

	@Column(name = "letter_language", length = 10)
	private String letterLanguage;

	@Column(columnDefinition = "text")
	private String review;

	@Column(nullable = false)
	private int revisions;

	@Column(name = "error_code", length = 40)
	private String errorCode;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	protected AgentRun() {
		// required by JPA
	}

	public AgentRun(UUID applicationId, UUID userId, Instant createdAt) {
		this.applicationId = applicationId;
		this.userId = userId;
		this.status = RunStatus.PENDING;
		this.createdAt = createdAt;
	}

	public void start(Instant now) {
		this.status = RunStatus.RUNNING;
		this.startedAt = now;
	}

	public void working(AgentName agent) {
		this.currentAgent = agent;
	}

	public void matched(String matchResultJson) {
		this.matchResult = matchResultJson;
	}

	public void lettered(String letter, String language, String reviewJson, int revisions) {
		this.coverLetter = letter;
		this.letterLanguage = language;
		this.review = reviewJson;
		this.revisions = revisions;
	}

	public void finish(RunStatus finalStatus, String errorCode, Instant now) {
		this.status = finalStatus;
		this.errorCode = errorCode;
		this.currentAgent = null;
		this.finishedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getApplicationId() {
		return applicationId;
	}

	public UUID getUserId() {
		return userId;
	}

	public RunStatus getStatus() {
		return status;
	}

	public AgentName getCurrentAgent() {
		return currentAgent;
	}

	public String getMatchResult() {
		return matchResult;
	}

	public String getCoverLetter() {
		return coverLetter;
	}

	public String getLetterLanguage() {
		return letterLanguage;
	}

	public String getReview() {
		return review;
	}

	public int getRevisions() {
		return revisions;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}

}
