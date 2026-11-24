package com.applypilot.insights;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.applypilot.agent.AgentRunRepository;
import com.applypilot.agent.AgentRunRepository.ApplicationScore;
import com.applypilot.analysis.AiUsageLimiter;
import com.applypilot.common.ai.AiProperties;
import com.applypilot.insights.dto.InsightsResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** The SQL that picks each application's latest score is covered by {@code InsightsFlowIT}. */
@ExtendWith(MockitoExtension.class)
class InsightsServiceTest {

	private static final UUID USER_ID = UUID.randomUUID();

	@Mock
	private AgentRunRepository runs;

	@Mock
	private AiUsageLimiter limiter;

	@Test
	void returnsScoresByApplicationAndTodaysAllowance() {
		UUID matched = UUID.randomUUID();
		when(runs.findLatestScores(USER_ID)).thenReturn(List.of(score(matched, 83)));
		when(limiter.remaining(USER_ID)).thenReturn(7);

		InsightsResponse response = service(10).get(USER_ID);

		assertThat(response.matchScores()).containsExactlyEntriesOf(Map.of(matched, 83));
		assertThat(response.aiCallsLeft()).isEqualTo(7);
		assertThat(response.aiDailyLimit()).isEqualTo(10);
	}

	@Test
	void leavesOutRunsWhoseStoredMatchHasNoScore() {
		when(runs.findLatestScores(USER_ID)).thenReturn(List.of(score(UUID.randomUUID(), null)));
		when(limiter.remaining(USER_ID)).thenReturn(0);

		assertThat(service(0).get(USER_ID).matchScores()).isEmpty();
	}

	private InsightsService service(int dailyLimit) {
		return new InsightsService(runs, limiter, new AiProperties("model", 0, 2000, "low", dailyLimit));
	}

	private static ApplicationScore score(UUID applicationId, Integer score) {
		return new ApplicationScore() {

			@Override
			public UUID getApplicationId() {
				return applicationId;
			}

			@Override
			public Integer getScore() {
				return score;
			}

		};
	}

}
