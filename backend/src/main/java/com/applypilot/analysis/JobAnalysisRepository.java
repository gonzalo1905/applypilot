package com.applypilot.analysis;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every finder takes the owner's id, so a query can never return another user's analysis.
 */
public interface JobAnalysisRepository extends JpaRepository<JobAnalysis, UUID> {

	Optional<JobAnalysis> findByApplicationIdAndUserId(UUID applicationId, UUID userId);

	/**
	 * Cache lookup: the most recent analysis this user got for exactly this posting text
	 * from the given source. Scoped to the user on purpose: pasted text may be private, so
	 * one user's result is never reused for another.
	 */
	Optional<JobAnalysis> findFirstByUserIdAndPostingHashAndSourceOrderByAnalyzedAtDesc(UUID userId,
			String postingHash, AnalysisSource source);

}
