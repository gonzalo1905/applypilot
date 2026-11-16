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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * One CV per user: upload (or replace), read metadata, download and delete.
 *
 * <p>A file on disk and a row in the database cannot be changed in one atomic step, so the
 * order of operations matters. The rule here: never delete a file that a committed row still
 * points to. Slow work (parsing the PDF, disk I/O) happens outside the database transaction,
 * which stays short and only covers the row change. That is why this class uses a
 * {@link TransactionTemplate} instead of {@code @Transactional} on the whole method.
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

	private final TransactionTemplate transaction;

	public CvService(CvRepository repository, CvStorage storage, PdfTextExtractor textExtractor,
			CvProperties properties, Clock clock, TransactionTemplate transaction) {
		this.repository = repository;
		this.storage = storage;
		this.textExtractor = textExtractor;
		this.properties = properties;
		this.clock = clock;
		this.transaction = transaction;
	}

	public CvResponse upload(UUID userId, String originalFileName, String contentType, byte[] content) {
		validate(contentType, content);
		String text = textExtractor.extractText(content);
		String fileName = sanitizeFileName(originalFileName);

		// 1. Store the new file under a fresh key. 2. Point the row at it (short transaction).
		// 3. Only after that commit, remove the old file. A failure at any step leaves the
		// user's previous CV fully intact.
		String newKey = "cvs/" + UUID.randomUUID() + ".pdf";
		SavedCv saved;
		try {
			storage.store(newKey, content);
			saved = transaction.execute(status -> {
				Cv cv = repository.findForUpdateByUserId(userId).orElseGet(() -> new Cv(userId));
				String previousKey = cv.getStorageKey();
				cv.replaceFile(fileName, PDF_CONTENT_TYPE, content.length, newKey, text, clock.instant());
				return new SavedCv(CvResponse.from(repository.saveAndFlush(cv)), previousKey);
			});
		}
		catch (DataIntegrityViolationException ex) {
			deleteQuietly(newKey);
			throw new CvUploadConflictException();
		}
		catch (RuntimeException ex) {
			deleteQuietly(newKey);
			throw ex;
		}
		if (saved.previousKey() != null) {
			deleteQuietly(saved.previousKey());
		}
		return saved.response();
	}

	@Transactional(readOnly = true)
	public CvResponse getMetadata(UUID userId) {
		return CvResponse.from(findOwned(userId));
	}

	public CvFile download(UUID userId) {
		Cv cv = findOwned(userId);
		byte[] content = storage.load(cv.getStorageKey()).orElseThrow(() -> {
			log.error("CV {} has no file in storage under its key", cv.getId());
			return new CvNotFoundException();
		});
		return new CvFile(cv.getFileName(), cv.getContentType(), content);
	}

	public void delete(UUID userId) {
		String key = transaction.execute(status -> {
			Cv cv = repository.findForUpdateByUserId(userId).orElseThrow(CvNotFoundException::new);
			repository.delete(cv);
			repository.flush();
			return cv.getStorageKey();
		});
		// The row is gone for good (committed), so the file can go too.
		deleteQuietly(key);
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

	private record SavedCv(CvResponse response, String previousKey) {
	}

}
