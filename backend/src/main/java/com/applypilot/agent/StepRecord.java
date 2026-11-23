package com.applypilot.agent;

import java.time.Instant;

/** Everything recorded about one step, before it becomes an {@link AgentRunStep} row. */
public record StepRecord(StepSource source, StepReason reason, String inputHash, String output, StepUsage usage,
		long latencyMs, Instant startedAt) {
}
