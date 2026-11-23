package com.applypilot.analysis;

import com.applypilot.common.ai.AiProperties;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enforces the per-user daily limit on AI calls ({@code app.ai.daily-limit}). The free tier of
 * the provider is shared by every user of the deployment, so one user must not be able to
 * use it all. "Today" is the server's date.
 */
@Component
public class AiUsageLimiter {

	private final AiUsageRepository repository;

	private final AiProperties properties;

	private final Clock clock;

	public AiUsageLimiter(AiUsageRepository repository, AiProperties properties, Clock clock) {
		this.repository = repository;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * Takes one of today's calls for the user.
	 *
	 * @throws AiDailyLimitReachedException if the user has none left
	 */
	@Transactional
	public void acquire(UUID userId) {
		int limit = properties.dailyLimit();
		if (limit <= 0 || repository.incrementIfBelowLimit(userId, LocalDate.now(clock), limit) == 0) {
			throw new AiDailyLimitReachedException(limit);
		}
	}

	/** Calls the user still has today; a quick check before starting multi-call work. */
	@Transactional(readOnly = true)
	public int remaining(UUID userId) {
		int used = repository.findByUserIdAndUsageDate(userId, LocalDate.now(clock)).map(AiUsage::getCalls).orElse(0);
		return Math.max(0, properties.dailyLimit() - used);
	}

}
