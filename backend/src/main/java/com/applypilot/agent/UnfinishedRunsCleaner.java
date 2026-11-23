package com.applypilot.agent;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs execute in memory, so a restart loses the ones in progress. At startup they are
 * marked FAILED with {@code INTERRUPTED}, so the user can start a new one instead of
 * waiting forever. Assumes a single backend instance.
 */
@Component
public class UnfinishedRunsCleaner {

	private static final Logger log = LoggerFactory.getLogger(UnfinishedRunsCleaner.class);

	private final AgentRunRepository runs;

	private final Clock clock;

	public UnfinishedRunsCleaner(AgentRunRepository runs, Clock clock) {
		this.runs = runs;
		this.clock = clock;
	}

	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void failUnfinishedRuns() {
		int failed = runs.failUnfinished(clock.instant());
		if (failed > 0) {
			log.warn("Marked {} agent runs interrupted by a restart as failed", failed);
		}
	}

}
