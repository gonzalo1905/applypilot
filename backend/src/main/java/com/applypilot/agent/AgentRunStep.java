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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One finished step of an {@link AgentRun}: the trace of what one agent did. */
@Entity
@Table(name = "agent_run_steps")
public class AgentRunStep {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "run_id", nullable = false, updatable = false)
	private UUID runId;

	@Column(name = "step_order", nullable = false, updatable = false)
	private int stepOrder;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, updatable = false)
	private AgentName agent;

	@Column(nullable = false, updatable = false)
	private int attempt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, updatable = false)
	private StepSource source;

	@Enumerated(EnumType.STRING)
	@Column(length = 40, updatable = false)
	private StepReason reason;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "input_hash", nullable = false, length = 64, columnDefinition = "char(64)", updatable = false)
	private String inputHash;

	@Column(columnDefinition = "text", updatable = false)
	private String output;

	@Column(length = 100, updatable = false)
	private String model;

	@Column(name = "model_calls", nullable = false, updatable = false)
	private int modelCalls;

	@Column(name = "tool_calls", nullable = false, updatable = false)
	private int toolCalls;

	@Column(name = "prompt_tokens", updatable = false)
	private Integer promptTokens;

	@Column(name = "completion_tokens", updatable = false)
	private Integer completionTokens;

	@Column(name = "latency_ms", nullable = false, updatable = false)
	private long latencyMs;

	@Column(name = "started_at", nullable = false, updatable = false)
	private Instant startedAt;

	protected AgentRunStep() {
		// required by JPA
	}

	public AgentRunStep(UUID runId, int stepOrder, AgentName agent, int attempt, StepRecord record) {
		this.runId = runId;
		this.stepOrder = stepOrder;
		this.agent = agent;
		this.attempt = attempt;
		this.source = record.source();
		this.reason = record.reason();
		this.inputHash = record.inputHash();
		this.output = record.output();
		this.model = record.usage().model();
		this.modelCalls = record.usage().modelCalls();
		this.toolCalls = record.usage().toolCalls();
		this.promptTokens = record.usage().promptTokens();
		this.completionTokens = record.usage().completionTokens();
		this.latencyMs = record.latencyMs();
		this.startedAt = record.startedAt();
	}

	public UUID getId() {
		return id;
	}

	public UUID getRunId() {
		return runId;
	}

	public int getStepOrder() {
		return stepOrder;
	}

	public AgentName getAgent() {
		return agent;
	}

	public int getAttempt() {
		return attempt;
	}

	public StepSource getSource() {
		return source;
	}

	public StepReason getReason() {
		return reason;
	}

	public String getInputHash() {
		return inputHash;
	}

	public String getOutput() {
		return output;
	}

	public String getModel() {
		return model;
	}

	public int getModelCalls() {
		return modelCalls;
	}

	public int getToolCalls() {
		return toolCalls;
	}

	public Integer getPromptTokens() {
		return promptTokens;
	}

	public Integer getCompletionTokens() {
		return completionTokens;
	}

	public long getLatencyMs() {
		return latencyMs;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

}
