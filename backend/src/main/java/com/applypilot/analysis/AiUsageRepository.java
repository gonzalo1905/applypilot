package com.applypilot.analysis;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiUsageRepository extends JpaRepository<AiUsage, UUID> {

	Optional<AiUsage> findByUserIdAndUsageDate(UUID userId, LocalDate usageDate);

	/**
	 * Counts one more AI call for the user and day, but only while the count is below the
	 * limit. Check and increment are one statement, so two simultaneous requests can never
	 * both take the last call: the database row lock decides who comes first.
	 *
	 * @return 1 if the call was counted, 0 if the limit was already reached
	 */
	@Modifying
	@Query(value = """
			insert into ai_usage (id, user_id, usage_date, calls)
			values (gen_random_uuid(), :userId, :usageDate, 1)
			on conflict (user_id, usage_date)
			do update set calls = ai_usage.calls + 1 where ai_usage.calls < :dailyLimit
			""", nativeQuery = true)
	int incrementIfBelowLimit(@Param("userId") UUID userId, @Param("usageDate") LocalDate usageDate,
			@Param("dailyLimit") int dailyLimit);

}
