package com.applypilot.token;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalAccessTokenRepository extends JpaRepository<PersonalAccessToken, UUID> {

	Optional<PersonalAccessToken> findByTokenHashAndRevokedAtIsNull(String tokenHash);

	List<PersonalAccessToken> findByUserIdOrderByCreatedAtDesc(UUID userId);

	Optional<PersonalAccessToken> findByIdAndUserId(UUID id, UUID userId);

	long countByUserIdAndRevokedAtIsNull(UUID userId);

}
