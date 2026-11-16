package com.applypilot.cv;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface CvRepository extends JpaRepository<Cv, UUID> {

	Optional<Cv> findByUserId(UUID userId);

	/**
	 * Same lookup, but locks the row until the transaction ends, so two replacements of the
	 * same CV cannot both read the old file key and leave one new file orphaned.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Cv> findForUpdateByUserId(UUID userId);

}
