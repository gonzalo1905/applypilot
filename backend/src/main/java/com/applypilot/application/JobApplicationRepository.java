package com.applypilot.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every finder takes the owner's id, so a query can never return another user's rows.
 * Do not call {@code findById} for user-facing reads: it skips the ownership filter.
 */
public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {

	List<JobApplication> findByUserIdOrderByPositionAscCreatedAtAsc(UUID userId);

	List<JobApplication> findByUserIdAndStatusOrderByPositionAscCreatedAtAsc(UUID userId, ApplicationStatus status);

	Optional<JobApplication> findByIdAndUserId(UUID id, UUID userId);

	int countByUserIdAndStatus(UUID userId, ApplicationStatus status);

	/**
	 * Locks the owner's row until the current transaction ends, so that changes to one
	 * user's board run one at a time. Without it, two overlapping requests (two tabs, quick
	 * drags) can renumber the same cards from stale data and overwrite each other, or
	 * deadlock by updating the same rows in opposite order. Different users never wait for
	 * each other.
	 */
	@Query(value = "select u.id from users u where u.id = :userId for update", nativeQuery = true)
	Optional<UUID> lockBoard(@Param("userId") UUID userId);

}
