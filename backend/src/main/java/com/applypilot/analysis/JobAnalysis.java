package com.applypilot.analysis;

import com.applypilot.application.WorkMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The structured data extracted from the job posting of one application, plus the posting
 * text itself and how the result was produced.
 */
@Entity
@Table(name = "job_analyses")
public class JobAnalysis {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "application_id", nullable = false, updatable = false, unique = true)
	private UUID applicationId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "posting_text", nullable = false, columnDefinition = "text")
	private String postingText;

	// Fixed-length hex digest: mapped as CHAR so schema validation matches the CHAR(64) column.
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "posting_hash", nullable = false, length = 64, columnDefinition = "char(64)")
	private String postingHash;

	@Column(length = 160)
	private String company;

	@Column(name = "role_title", length = 200)
	private String roleTitle;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Seniority seniority;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "required_skills", nullable = false, columnDefinition = "text[]")
	private List<String> requiredSkills;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "nice_to_have_skills", nullable = false, columnDefinition = "text[]")
	private List<String> niceToHaveSkills;

	@Enumerated(EnumType.STRING)
	@Column(name = "work_mode", length = 20)
	private WorkMode workMode;

	@Column(length = 160)
	private String location;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false, columnDefinition = "text[]")
	private List<String> languages;

	@Column(length = 160)
	private String salary;

	@Column(nullable = false, columnDefinition = "text")
	private String summary;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AnalysisSource source;

	@Enumerated(EnumType.STRING)
	@Column(name = "fallback_reason", length = 30)
	private FallbackReason fallbackReason;

	@Column(length = 100)
	private String model;

	@Column(name = "analyzed_at", nullable = false)
	private Instant analyzedAt;

	protected JobAnalysis() {
		// required by JPA
	}

	public JobAnalysis(UUID applicationId, UUID userId) {
		this.applicationId = applicationId;
		this.userId = userId;
	}

	/** Replaces the whole analysis with a new result (first run or re-run). */
	public void replace(String postingText, String postingHash, ExtractedPosting posting, AnalysisSource source,
			FallbackReason fallbackReason, String model, Instant analyzedAt) {
		this.postingText = postingText;
		this.postingHash = postingHash;
		this.company = posting.company();
		this.roleTitle = posting.roleTitle();
		this.seniority = posting.seniority();
		this.requiredSkills = List.copyOf(posting.requiredSkills());
		this.niceToHaveSkills = List.copyOf(posting.niceToHaveSkills());
		this.workMode = posting.workMode();
		this.location = posting.location();
		this.languages = List.copyOf(posting.languages());
		this.salary = posting.salary();
		this.summary = posting.summary();
		this.source = source;
		this.fallbackReason = fallbackReason;
		this.model = model;
		this.analyzedAt = analyzedAt;
	}

	/** The extracted fields, in the same shape the extractors produce. */
	public ExtractedPosting toExtractedPosting() {
		return new ExtractedPosting(company, roleTitle, seniority, requiredSkills, niceToHaveSkills, workMode,
				location, languages, salary, summary);
	}

	public UUID getId() {
		return id;
	}

	public UUID getApplicationId() {
		return applicationId;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getPostingText() {
		return postingText;
	}

	public String getPostingHash() {
		return postingHash;
	}

	public String getCompany() {
		return company;
	}

	public String getRoleTitle() {
		return roleTitle;
	}

	public Seniority getSeniority() {
		return seniority;
	}

	public List<String> getRequiredSkills() {
		return requiredSkills;
	}

	public List<String> getNiceToHaveSkills() {
		return niceToHaveSkills;
	}

	public WorkMode getWorkMode() {
		return workMode;
	}

	public String getLocation() {
		return location;
	}

	public List<String> getLanguages() {
		return languages;
	}

	public String getSalary() {
		return salary;
	}

	public String getSummary() {
		return summary;
	}

	public AnalysisSource getSource() {
		return source;
	}

	public FallbackReason getFallbackReason() {
		return fallbackReason;
	}

	public String getModel() {
		return model;
	}

	public Instant getAnalyzedAt() {
		return analyzedAt;
	}

}
