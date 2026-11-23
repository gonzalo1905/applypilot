package com.applypilot.analysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/**
 * How many AI calls one user made on one day. The per-user daily limit is checked against
 * this counter before every call to the model.
 */
@Entity
@Table(name = "ai_usage")
public class AiUsage {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "usage_date", nullable = false, updatable = false)
	private LocalDate usageDate;

	@Column(nullable = false)
	private int calls;

	protected AiUsage() {
		// required by JPA
	}

	public AiUsage(UUID userId, LocalDate usageDate) {
		this.userId = userId;
		this.usageDate = usageDate;
	}

	public void increment() {
		this.calls++;
	}

	public int getCalls() {
		return calls;
	}

	public UUID getUserId() {
		return userId;
	}

	public LocalDate getUsageDate() {
		return usageDate;
	}

}
