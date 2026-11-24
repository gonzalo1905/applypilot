package com.applypilot.agent;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentRunRepository extends JpaRepository<AgentRun, UUID> {

	Optional<AgentRun> findFirstByApplicationIdAndUserIdOrderByCreatedAtDesc(UUID applicationId, UUID userId);

	Optional<AgentRun> findFirstByApplicationIdAndUserIdAndStatusIn(UUID applicationId, UUID userId,
			Collection<RunStatus> statuses);

	/**
	 * The score of each application's latest run that produced a match, for one user. The
	 * match is stored as JSON text; PostgreSQL reads the score out of it.
	 */
	@Query(value = """
			select distinct on (r.application_id) r.application_id as "applicationId",
			cast(cast(r.match_result as jsonb) ->> 'score' as integer) as "score"
			from agent_runs r
			where r.user_id = :userId and r.match_result is not null
			order by r.application_id, r.created_at desc
			""", nativeQuery = true)
	List<ApplicationScore> findLatestScores(@Param("userId") UUID userId);

	/** One row of {@link #findLatestScores}. */
	interface ApplicationScore {

		UUID getApplicationId();

		Integer getScore();

	}

	/**
	 * Marks runs that a stopped server left unfinished. Their worker thread is gone, so
	 * without this they would look busy forever.
	 */
	@Modifying
	@Query("""
			update AgentRun r set r.status = com.applypilot.agent.RunStatus.FAILED, r.currentAgent = null,
			r.errorCode = 'INTERRUPTED', r.finishedAt = :now
			where r.status in (com.applypilot.agent.RunStatus.PENDING, com.applypilot.agent.RunStatus.RUNNING)
			""")
	int failUnfinished(@Param("now") Instant now);

}
