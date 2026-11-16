package com.applypilot.cv;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CvRepository extends JpaRepository<Cv, UUID> {

	Optional<Cv> findByUserId(UUID userId);

}
