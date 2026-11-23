package com.applypilot.token;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A read-only credential for the MCP server. The token itself is never stored. */
@Entity
@Table(name = "personal_access_tokens")
public class PersonalAccessToken {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(nullable = false, length = 60, updatable = false)
	private String name;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "token_hash", nullable = false, length = 64, columnDefinition = "char(64)", updatable = false)
	private String tokenHash;

	@Column(name = "token_prefix", nullable = false, length = 16, updatable = false)
	private String tokenPrefix;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "last_used_at")
	private Instant lastUsedAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	protected PersonalAccessToken() {
		// required by JPA
	}

	public PersonalAccessToken(UUID userId, String name, String tokenHash, String tokenPrefix, Instant createdAt) {
		this.userId = userId;
		this.name = name;
		this.tokenHash = tokenHash;
		this.tokenPrefix = tokenPrefix;
		this.createdAt = createdAt;
	}

	public void used(Instant now) {
		this.lastUsedAt = now;
	}

	public void revoke(Instant now) {
		if (revokedAt == null) {
			this.revokedAt = now;
		}
	}

	public boolean isActive() {
		return revokedAt == null;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getName() {
		return name;
	}

	public String getTokenPrefix() {
		return tokenPrefix;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getLastUsedAt() {
		return lastUsedAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

}
