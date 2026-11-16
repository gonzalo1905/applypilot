package com.applypilot.cv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.applypilot.common.error.InvalidRequestException;
import com.applypilot.cv.CvService.CvFile;
import com.applypilot.cv.dto.CvResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class CvServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");

	private static final UUID USER_ID = UUID.randomUUID();

	private static final byte[] PDF = "%PDF-1.7 fake but well signed".getBytes(StandardCharsets.US_ASCII);

	@Mock
	private CvRepository repository;

	@Mock
	private CvStorage storage;

	@Mock
	private PdfTextExtractor textExtractor;

	private CvService service;

	@BeforeEach
	void setUp() {
		CvProperties properties = new CvProperties(DataSize.ofBytes(64), "unused");
		service = new CvService(repository, storage, textExtractor, properties, Clock.fixed(NOW, ZoneOffset.UTC),
				directTransactions());
	}

	@Test
	void firstUploadStoresTheFileAndSavesMetadataWithTheExtractedText() {
		when(textExtractor.extractText(PDF)).thenReturn("Java developer");
		when(repository.findForUpdateByUserId(USER_ID)).thenReturn(Optional.empty());
		when(repository.saveAndFlush(any(Cv.class))).thenAnswer(invocation -> invocation.getArgument(0));

		CvResponse response = service.upload(USER_ID, "C:\\Users\\ada\\My CV.pdf", "application/pdf", PDF);

		ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
		verify(storage).store(key.capture(), eq(PDF));
		assertThat(key.getValue()).matches("cvs/[0-9a-f-]{36}\\.pdf");

		ArgumentCaptor<Cv> saved = ArgumentCaptor.forClass(Cv.class);
		verify(repository).saveAndFlush(saved.capture());
		assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
		assertThat(saved.getValue().getStorageKey()).isEqualTo(key.getValue());
		assertThat(saved.getValue().getExtractedText()).isEqualTo("Java developer");
		assertThat(saved.getValue().getUploadedAt()).isEqualTo(NOW);

		assertThat(response.fileName()).isEqualTo("My CV.pdf");
		assertThat(response.sizeBytes()).isEqualTo(PDF.length);
		assertThat(response.textExtracted()).isTrue();
		assertThat(response.extractedCharacters()).isEqualTo("Java developer".length());
		verify(storage, never()).delete(anyString());
	}

	@Test
	void replacingACvDeletesThePreviousFileAfterTheNewOneIsSaved() {
		Cv existing = existingCv("cvs/old.pdf");
		when(textExtractor.extractText(PDF)).thenReturn("");
		when(repository.findForUpdateByUserId(USER_ID)).thenReturn(Optional.of(existing));
		when(repository.saveAndFlush(existing)).thenReturn(existing);

		CvResponse response = service.upload(USER_ID, "new.pdf", "application/pdf", PDF);

		assertThat(existing.getStorageKey()).isNotEqualTo("cvs/old.pdf");
		assertThat(existing.getFileName()).isEqualTo("new.pdf");
		// Order matters: the old file may only go once the record points at the new one.
		InOrder order = inOrder(storage, repository);
		order.verify(storage).store(anyString(), eq(PDF));
		order.verify(repository).saveAndFlush(existing);
		order.verify(storage).delete("cvs/old.pdf");
		// A scanned PDF has no text: the upload still succeeds and says so.
		assertThat(response.textExtracted()).isFalse();
	}

	@Test
	void aFailedSaveRemovesTheNewFileAndKeepsThePreviousOne() {
		Cv existing = existingCv("cvs/old.pdf");
		when(textExtractor.extractText(PDF)).thenReturn("text");
		when(repository.findForUpdateByUserId(USER_ID)).thenReturn(Optional.of(existing));
		when(repository.saveAndFlush(existing)).thenThrow(new IllegalStateException("database is down"));

		assertThatThrownBy(() -> service.upload(USER_ID, "new.pdf", "application/pdf", PDF))
			.isInstanceOf(IllegalStateException.class);

		ArgumentCaptor<String> newKey = ArgumentCaptor.forClass(String.class);
		verify(storage).store(newKey.capture(), eq(PDF));
		verify(storage).delete(newKey.getValue());
		verify(storage, never()).delete("cvs/old.pdf");
	}

	@Test
	void aFailedFileWriteLeavesNoHalfWrittenFileAndNeverTouchesTheDatabase() {
		when(textExtractor.extractText(PDF)).thenReturn("text");
		doThrow(new IllegalStateException("disk full")).when(storage).store(anyString(), eq(PDF));

		assertThatThrownBy(() -> service.upload(USER_ID, "new.pdf", "application/pdf", PDF))
			.isInstanceOf(IllegalStateException.class);

		ArgumentCaptor<String> newKey = ArgumentCaptor.forClass(String.class);
		verify(storage).store(newKey.capture(), eq(PDF));
		verify(storage).delete(newKey.getValue());
		verifyNoInteractions(repository);
	}

	@Test
	void twoFirstUploadsAtOnceReportAConflictInsteadOfAServerError() {
		when(textExtractor.extractText(PDF)).thenReturn("text");
		when(repository.findForUpdateByUserId(USER_ID)).thenReturn(Optional.empty());
		when(repository.saveAndFlush(any(Cv.class))).thenThrow(new DataIntegrityViolationException("uk_cvs_user"));

		assertThatThrownBy(() -> service.upload(USER_ID, "cv.pdf", "application/pdf", PDF))
			.isInstanceOf(CvUploadConflictException.class);

		ArgumentCaptor<String> newKey = ArgumentCaptor.forClass(String.class);
		verify(storage).store(newKey.capture(), eq(PDF));
		verify(storage).delete(newKey.getValue());
	}

	@Test
	void rejectsAnEmptyFile() {
		assertThatThrownBy(() -> service.upload(USER_ID, "cv.pdf", "application/pdf", new byte[0]))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessage("The file is empty");
		verifyNoInteractions(storage, repository, textExtractor);
	}

	@Test
	void rejectsAFileLargerThanTheLimit() {
		byte[] tooLarge = new byte[65];

		assertThatThrownBy(() -> service.upload(USER_ID, "cv.pdf", "application/pdf", tooLarge))
			.isInstanceOf(FileTooLargeException.class);
		verifyNoInteractions(storage, repository, textExtractor);
	}

	@Test
	void rejectsAFileThatIsNotDeclaredAsPdf() {
		assertThatThrownBy(() -> service.upload(USER_ID, "cv.pdf", "image/png", PDF))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessage("Only PDF files are accepted");
		assertThatThrownBy(() -> service.upload(USER_ID, "cv.pdf", null, PDF))
			.isInstanceOf(InvalidRequestException.class);
		verifyNoInteractions(storage, repository, textExtractor);
	}

	@Test
	void rejectsAFileThatClaimsToBePdfButIsNot() {
		byte[] executable = "MZ this is a renamed executable".getBytes(StandardCharsets.US_ASCII);

		assertThatThrownBy(() -> service.upload(USER_ID, "cv.pdf", "application/pdf", executable))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessage("Only PDF files are accepted");
		verifyNoInteractions(storage, repository, textExtractor);
	}

	@Test
	void downloadReturnsTheStoredBytesWithTheOriginalName() {
		when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(existingCv("cvs/old.pdf")));
		when(storage.load("cvs/old.pdf")).thenReturn(Optional.of(PDF));

		CvFile file = service.download(USER_ID);

		assertThat(file.fileName()).isEqualTo("old.pdf");
		assertThat(file.contentType()).isEqualTo("application/pdf");
		assertThat(file.content()).isEqualTo(PDF);
	}

	@Test
	void metadataDownloadAndDeleteFailWhenTheUserHasNoCv() {
		when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getMetadata(USER_ID)).isInstanceOf(CvNotFoundException.class);
		assertThatThrownBy(() -> service.download(USER_ID)).isInstanceOf(CvNotFoundException.class);
		assertThatThrownBy(() -> service.delete(USER_ID)).isInstanceOf(CvNotFoundException.class);
		verifyNoInteractions(storage);
	}

	@Test
	void downloadFailsWhenTheFileIsMissingFromStorage() {
		when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(existingCv("cvs/old.pdf")));
		when(storage.load("cvs/old.pdf")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.download(USER_ID)).isInstanceOf(CvNotFoundException.class);
	}

	@Test
	void deleteRemovesTheRecordAndTheFile() {
		Cv existing = existingCv("cvs/old.pdf");
		when(repository.findForUpdateByUserId(USER_ID)).thenReturn(Optional.of(existing));

		service.delete(USER_ID);

		verify(repository).delete(existing);
		verify(storage).delete("cvs/old.pdf");
	}

	@Test
	void deleteSucceedsEvenIfTheFileCannotBeRemoved() {
		Cv existing = existingCv("cvs/old.pdf");
		when(repository.findForUpdateByUserId(USER_ID)).thenReturn(Optional.of(existing));
		doThrow(new IllegalStateException("disk error")).when(storage).delete("cvs/old.pdf");

		service.delete(USER_ID);

		verify(repository).delete(existing);
	}

	@Test
	void sanitizesFileNames() {
		assertThat(CvService.sanitizeFileName("../../etc/passwd")).isEqualTo("passwd");
		assertThat(CvService.sanitizeFileName("C:\\temp\\cv final.pdf")).isEqualTo("cv final.pdf");
		assertThat(CvService.sanitizeFileName("  ")).isEqualTo("cv.pdf");
		assertThat(CvService.sanitizeFileName(null)).isEqualTo("cv.pdf");
		assertThat(CvService.sanitizeFileName("a".repeat(300) + ".pdf")).hasSize(255).endsWith(".pdf");
	}

	/** Runs the callback directly: unit tests have no database, so there is nothing to commit. */
	private static TransactionTemplate directTransactions() {
		return new TransactionTemplate() {
			@Override
			public <T> T execute(TransactionCallback<T> action) {
				return action.doInTransaction(new SimpleTransactionStatus());
			}
		};
	}

	private static Cv existingCv(String storageKey) {
		Cv cv = new Cv(USER_ID);
		cv.replaceFile("old.pdf", "application/pdf", 10, storageKey, "old text", NOW.minusSeconds(60));
		return cv;
	}

}
