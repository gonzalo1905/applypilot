package com.applypilot.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One job application: a card on the user's Kanban board.
 *
 * <p>Named {@code JobApplication} rather than {@code Application} to avoid confusion with the
 * Spring Boot application class. The owner is stored as a plain id: every query filters by it,
 * and nothing here needs to navigate to the {@code User} entity.
 */
@Entity
@Table(name = "job_applications")
public class JobApplication {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(nullable = false, length = 120)
	private String company;

	@Column(name = "role_title", nullable = false, length = 160)
	private String roleTitle;

	@Column(name = "job_url", length = 2048)
	private String jobUrl;

	@Column(length = 120)
	private String location;

	@Enumerated(EnumType.STRING)
	@Column(name = "work_mode", length = 20)
	private WorkMode workMode;

	@Column(name = "salary_note", length = 120)
	private String salaryNote;

	@Column(columnDefinition = "text")
	private String notes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ApplicationStatus status;

	/** Zero-based order of the card inside its column. */
	@Column(nullable = false)
	private int position;

	@Column(name = "applied_at")
	private LocalDate appliedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected JobApplication() {
		// required by JPA
	}

	public JobApplication(UUID userId, ApplicationStatus status, int position) {
		this.userId = userId;
		this.status = status;
		this.position = position;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	/**
	 * Records that the user changed this card. Called explicitly by the service instead of a
	 * JPA {@code @PreUpdate} hook, so cards that are merely renumbered when a neighbor moves
	 * do not look "recently updated".
	 */
	public void touch(Instant now) {
		this.updatedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getCompany() {
		return company;
	}

	public void setCompany(String company) {
		this.company = company;
	}

	public String getRoleTitle() {
		return roleTitle;
	}

	public void setRoleTitle(String roleTitle) {
		this.roleTitle = roleTitle;
	}

	public String getJobUrl() {
		return jobUrl;
	}

	public void setJobUrl(String jobUrl) {
		this.jobUrl = jobUrl;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public WorkMode getWorkMode() {
		return workMode;
	}

	public void setWorkMode(WorkMode workMode) {
		this.workMode = workMode;
	}

	public String getSalaryNote() {
		return salaryNote;
	}

	public void setSalaryNote(String salaryNote) {
		this.salaryNote = salaryNote;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public ApplicationStatus getStatus() {
		return status;
	}

	public void setStatus(ApplicationStatus status) {
		this.status = status;
	}

	public int getPosition() {
		return position;
	}

	public void setPosition(int position) {
		this.position = position;
	}

	public LocalDate getAppliedAt() {
		return appliedAt;
	}

	public void setAppliedAt(LocalDate appliedAt) {
		this.appliedAt = appliedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
