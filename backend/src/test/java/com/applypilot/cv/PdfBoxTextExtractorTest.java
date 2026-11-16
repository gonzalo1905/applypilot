package com.applypilot.cv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.applypilot.common.error.InvalidRequestException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PdfBoxTextExtractorTest {

	private final PdfBoxTextExtractor extractor = new PdfBoxTextExtractor();

	@Test
	void extractsTheTextOfAPdf() {
		String text = extractor.extractText(TestPdfs.withText("Java developer with 5 years of Spring Boot"));

		assertThat(text).isEqualTo("Java developer with 5 years of Spring Boot");
	}

	@Test
	void returnsEmptyTextForAPdfWithoutATextLayer() {
		assertThat(extractor.extractText(TestPdfs.withoutText())).isEmpty();
	}

	@Test
	void readsOnlyTheFirstPagesOfALongDocument() {
		byte[] pdf = TestPdfs.withPages(PdfBoxTextExtractor.MAX_PAGES + 1, number -> "page-" + number + "-end");

		String text = extractor.extractText(pdf);

		assertThat(text).contains("page-1-end").contains("page-" + PdfBoxTextExtractor.MAX_PAGES + "-end");
		assertThat(text).doesNotContain("page-" + (PdfBoxTextExtractor.MAX_PAGES + 1) + "-end");
	}

	@Test
	void truncatesVeryLongText() {
		// 30 pages of 7,000 characters each: more than the limit.
		byte[] pdf = TestPdfs.withPages(PdfBoxTextExtractor.MAX_PAGES, number -> "a".repeat(7_000));

		assertThat(extractor.extractText(pdf)).hasSize(PdfBoxTextExtractor.MAX_CHARACTERS);
	}

	@Test
	void rejectsAPdfThatNeedsAPasswordToOpen() {
		byte[] locked = TestPdfs.encrypted("Secret CV", "user-secret", true);

		assertThatThrownBy(() -> extractor.extractText(locked)).isInstanceOf(InvalidRequestException.class)
			.hasMessage("Password-protected PDFs are not supported");
	}

	@Test
	void rejectsAPdfThatForbidsCopyingItsText() {
		byte[] restricted = TestPdfs.encrypted("Restricted CV", "", false);

		assertThatThrownBy(() -> extractor.extractText(restricted)).isInstanceOf(InvalidRequestException.class)
			.hasMessage("This PDF does not allow its text to be read");
	}

	@Test
	void readsAnEncryptedPdfThatOpensFreelyAndAllowsCopying() {
		byte[] open = TestPdfs.encrypted("Open CV", "", true);

		assertThat(extractor.extractText(open)).isEqualTo("Open CV");
	}

	@Test
	void rejectsBytesThatOnlyPretendToBeAPdf() {
		byte[] fake = "%PDF-1.7 this is not really a pdf".getBytes(StandardCharsets.US_ASCII);

		assertThatThrownBy(() -> extractor.extractText(fake)).isInstanceOf(InvalidRequestException.class)
			.hasMessage("The file is not a readable PDF");
	}

}
