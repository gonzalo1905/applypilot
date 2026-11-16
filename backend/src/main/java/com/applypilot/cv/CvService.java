package com.applypilot.cv;

import com.applypilot.common.error.InvalidRequestException;
import com.applypilot.cv.dto.CvResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One CV per user: upload (or replace), read metadata, download and delete.
 */
@Service
public class CvService {

	static final String PDF_CONTENT_TYPE = "application/pdf";

	/** Every PDF file starts with these bytes, whatever its name or declared type. */
	private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);

	private static final int MAX_FILE_NAME_LENGTH = 255;

	private static final Logger log = LoggerFactory.getLogger(CvService.class);

	private final CvRepository repository;

	private final CvStorage storage;

	private final PdfTextExtractor textExtractor;

	private final CvProperties properties;

	private final Clock clock;

	public CvService(CvRepository repository, CvStorage storage, PdfTextExtractor textExtractor,
			CvProperties properties, Clock clock) {
		this.repository = repository;
		this.storage = storage;
		this.textExtractor = textExtractor;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional
	public CvResponse upload(UUID userId, String originalFileName, String contentType, byte[] content) {
		validate(contentType, content);
		String text = textExtractor.extractText(content);

		// Store under a fresh key, then repoint the record, then remove the old file. If
		// anything fails before the record is saved, the user's previous CV is still intact.
		String newKey = "cvs/" + UUID.randomUUID() + ".pdf";
		storage.store(newKey, content);
		String oldKey;
		Cv cv;
		try {
			cv = repository.findByUserId(userId).orElseGet(() -> new Cv(userId));
			oldKey = cv.getStorageKey();
			cv.replaceFile(sanitizeFileName(originalFileName), PDF_CONTENT_TYPE, content.length, newKey, text,
					clock.instant());
			cv = repository.saveAndFlush(cv);
		}
		catch (RuntimeException ex) {
			deleteQuietly(newKey);
			throw ex;
		}
		if (oldKey != null) {
			deleteQuietly(oldKey);
		}
		return CvResponse.from(cv);
	}

	@Transactional(readOnly = true)
	public CvResponse getMetadata(UUID userId) {
		return CvResponse.from(findOwned(userId));
	}

	@Transactional(readOnly = true)
	public CvFile download(UUID userId) {
		Cv cv = findOwned(userId);
		byte[] content = storage.load(cv.getStorageKey()).orElseThrow(() -> {
			log.error("CV {} has no file in storage under its key", cv.getId());
			return new CvNotFoundException();
		});
		return new CvFile(cv.getFileName(), cv.getContentType(), content);
	}

	@Transactional
	public void delete(UUID userId) {
		Cv cv = findOwned(userId);
		repository.delete(cv);
		repository.flush();
		deleteQuietly(cv.getStorageKey());
	}

	private Cv findOwned(UUID userId) {
		return repository.findByUserId(userId).orElseThrow(CvNotFoundException::new);
	}

	private void validate(String contentType, byte[] content) {
		if (content == null || content.length == 0) {
			throw new InvalidRequestException("The file is empty");
		}
		if (content.length > properties.maxSize().toBytes()) {
			throw new FileTooLargeException(properties.maxSize());
		}
		// The declared type and the file name are chosen by the client, so they prove
		// nothing. The signature check looks at what the file actually is.
		boolean declaredPdf = contentType != null
				&& contentType.toLowerCase(Locale.ROOT).startsWith(PDF_CONTENT_TYPE);
		boolean looksLikePdf = content.length >= PDF_SIGNATURE.length
				&& Arrays.equals(content, 0, PDF_SIGNATURE.length, PDF_SIGNATURE, 0, PDF_SIGNATURE.length);
		if (!declaredPdf || !looksLikePdf) {
			throw new InvalidRequestException("Only PDF files are accepted");
		}
	}

	/**
	 * Keeps only a safe display name: no folders, no control characters, bounded length.
	 * The name is never used to build a path on disk.
	 */
	static String sanitizeFileName(String originalFileName) {
		String name = originalFileName == null ? "" : originalFileName;
		name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
		name = name.replaceAll("\\p{Cntrl}", "").trim();
		if (name.isEmpty()) {
			return "cv.pdf";
		}
		return name.length() > MAX_FILE_NAME_LENGTH ? name.substring(name.length() - MAX_FILE_NAME_LENGTH) : name;
	}

	/** Cleanup must never turn a successful request into a failure; an orphan file is logged. */
	private void deleteQuietly(String key) {
		try {
			storage.delete(key);
		}
		catch (RuntimeException ex) {
			log.warn("Could not delete stored file {}", key, ex);
		}
	}

	/** A downloaded CV: its display name, type and bytes. */
	public record CvFile(String fileName, String contentType, byte[] content) {
	}

}
