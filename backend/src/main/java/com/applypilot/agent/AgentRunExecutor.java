package com.applypilot.agent;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Runs agent runs in the background on a small, bounded pool. A run takes from seconds to a
 * minute; holding the HTTP request that long would tie up a server thread and time out in
 * browsers and proxies, so the request only queues the run and the client polls.
 *
 * <p>Two workers keep the shared provider quota from being drained by parallel runs; a full
 * queue refuses new runs instead of growing without limit. Not registered as a Spring
 * {@code Executor} bean on purpose, so it cannot replace the application's default executor.
 */
@Component
public class AgentRunExecutor {

	static final int WORKERS = 2;

	static final int QUEUE_CAPACITY = 20;

	private final ThreadPoolExecutor pool;

	public AgentRunExecutor() {
		AtomicInteger counter = new AtomicInteger();
		this.pool = new ThreadPoolExecutor(WORKERS, WORKERS, 0, TimeUnit.SECONDS,
				new ArrayBlockingQueue<>(QUEUE_CAPACITY), task -> {
					Thread thread = new Thread(task, "agent-run-" + counter.incrementAndGet());
					thread.setDaemon(true);
					return thread;
				});
	}

	/** @throws RejectedExecutionException when the queue is full */
	public void submit(Runnable run) {
		pool.execute(run);
	}

	@PreDestroy
	void stop() {
		// Unfinished runs are marked as interrupted at the next start (UnfinishedRunsCleaner).
		pool.shutdownNow();
	}

}
