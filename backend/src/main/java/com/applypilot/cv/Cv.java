package com.applypilot.cv;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Metadata of a user's CV plus the text extracted from it. The PDF itself is not in the
 * database: {@code storageKey} points to it inside the {@link CvStorage} backend.
 */
@Entity
@Table(name = "cvs")
public class Cv {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false, unique = true)
	private UUID userId;

	@Column(name = "file_name", nullable = false)
	private String fileName;

	@Column(name = "content_type", nullable = false, length = 100)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Column(name = "storage_key", nullable = false)
	private String storageKey;

	@Column(name = "extracted_text", nullable = false, columnDefinition = "text")
	private String extractedText;

	@Column(name = "uploaded_at", nullable = false)
	private Instant uploadedAt;

	protected Cv() {
		// required by JPA
	}

	public Cv(UUID userId) {
		this.userId = userId;
	}

	/** Points this record at a newly stored file (first upload or replacement). */
	public void replaceFile(String fileName, String contentType, long sizeBytes, String storageKey,
			String extractedText, Instant uploadedAt) {
		this.fileName = fileName;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.storageKey = storageKey;
		this.extractedText = extractedText;
		this.uploadedAt = uploadedAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getFileName() {
		return fileName;
	}

	public String getContentType() {
		return contentType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public String getStorageKey() {
		return storageKey;
	}

	public String getExtractedText() {
		return extractedText;
	}

	public Instant getUploadedAt() {
		return uploadedAt;
	}

}
