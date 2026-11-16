package com.applypilot.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every finder takes the owner's id, so a query can never return another user's rows.
 * Do not call {@code findById} for user-facing reads: it skips the ownership filter.
 */
public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {

	List<JobApplication> findByUserIdOrderByPositionAscCreatedAtAsc(UUID userId);

	List<JobApplication> findByUserIdAndStatusOrderByPositionAscCreatedAtAsc(UUID userId, ApplicationStatus status);

	Optional<JobApplication> findByIdAndUserId(UUID id, UUID userId);

	int countByUserIdAndStatus(UUID userId, ApplicationStatus status);

}
