package com.applypilot.insights;

import com.applypilot.agent.AgentRunRepository;
import com.applypilot.analysis.AiUsageLimiter;
import com.applypilot.common.ai.AiProperties;
import com.applypilot.insights.dto.InsightsResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only summary of the caller's board: match scores and today's AI allowance. */
@Service
public class InsightsService {

	private final AgentRunRepository runs;

	private final AiUsageLimiter limiter;

	private final AiProperties aiProperties;

	public InsightsService(AgentRunRepository runs, AiUsageLimiter limiter, AiProperties aiProperties) {
		this.runs = runs;
		this.limiter = limiter;
		this.aiProperties = aiProperties;
	}

	@Transactional(readOnly = true)
	public InsightsResponse get(UUID userId) {
		Map<UUID, Integer> scores = new LinkedHashMap<>();
		for (AgentRunRepository.ApplicationScore row : runs.findLatestScores(userId)) {
			if (row.getScore() != null) {
				scores.put(row.getApplicationId(), row.getScore());
			}
		}
		return new InsightsResponse(scores, limiter.remaining(userId), Math.max(0, aiProperties.dailyLimit()));
	}

}
