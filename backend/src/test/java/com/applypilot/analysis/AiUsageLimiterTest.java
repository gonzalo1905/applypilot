package com.applypilot.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.applypilot.common.ai.AiProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** The SQL itself (atomic check and increment) is covered by {@code AnalysisFlowIT}. */
@ExtendWith(MockitoExtension.class)
class AiUsageLimiterTest {

	private static final UUID USER_ID = UUID.randomUUID();

	/** Late evening in UTC: the counter uses the clock's date, not a date computed elsewhere. */
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-20T23:59:00Z"), ZoneOffset.UTC);

	@Mock
	private AiUsageRepository repository;

	@Test
	void countsTheCallOnTodaysRowWhileBelowTheLimit() {
		when(repository.incrementIfBelowLimit(USER_ID, LocalDate.of(2026, 11, 20), 20)).thenReturn(1);

		assertThatCode(() -> limiter(20).acquire(USER_ID)).doesNotThrowAnyException();
	}

	@Test
	void answersTooManyRequestsWhenTheDatabaseRefusesTheIncrement() {
		when(repository.incrementIfBelowLimit(USER_ID, LocalDate.of(2026, 11, 20), 20)).thenReturn(0);

		assertThatThrownBy(() -> limiter(20).acquire(USER_ID)).isInstanceOf(AiDailyLimitReachedException.class)
			.hasMessageContaining("20 AI calls");
		verify(repository).incrementIfBelowLimit(USER_ID, LocalDate.of(2026, 11, 20), 20);
	}

	@Test
	void aLimitOfZeroDisablesAiCallsWithoutTouchingTheDatabase() {
		assertThatThrownBy(() -> limiter(0).acquire(USER_ID)).isInstanceOf(AiDailyLimitReachedException.class);
		verifyNoInteractions(repository);
	}

	@Test
	void reportsTheCallsLeftTodayAndNeverLessThanZero() {
		AiUsage usage = mock(AiUsage.class);
		when(usage.getCalls()).thenReturn(18);
		when(repository.findByUserIdAndUsageDate(USER_ID, LocalDate.of(2026, 11, 20))).thenReturn(Optional.of(usage));

		assertThat(limiter(20).remaining(USER_ID)).isEqualTo(2);
		assertThat(limiter(10).remaining(USER_ID)).isZero();
	}

	@Test
	void aUserWithoutCallsTodayHasTheWholeLimit() {
		when(repository.findByUserIdAndUsageDate(USER_ID, LocalDate.of(2026, 11, 20))).thenReturn(Optional.empty());

		assertThat(limiter(30).remaining(USER_ID)).isEqualTo(30);
	}

	private AiUsageLimiter limiter(int dailyLimit) {
		return new AiUsageLimiter(repository, new AiProperties("openai/gpt-oss-120b", 0, 2000, "low", dailyLimit),
				CLOCK);
	}

}
