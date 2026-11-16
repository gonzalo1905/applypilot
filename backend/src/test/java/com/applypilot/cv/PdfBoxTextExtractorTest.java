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
	void rejectsBytesThatOnlyPretendToBeAPdf() {
		byte[] fake = "%PDF-1.7 this is not really a pdf".getBytes(StandardCharsets.US_ASCII);

		assertThatThrownBy(() -> extractor.extractText(fake)).isInstanceOf(InvalidRequestException.class)
			.hasMessage("The file is not a readable PDF");
	}

}
