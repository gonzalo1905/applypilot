package com.applypilot.analysis;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiUsageRepository extends JpaRepository<AiUsage, UUID> {

	Optional<AiUsage> findByUserIdAndUsageDate(UUID userId, LocalDate usageDate);

}
