package com.applypilot.cv;

import com.applypilot.common.error.InvalidRequestException;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class PdfBoxTextExtractor implements PdfTextExtractor {

	/** A CV is a few pages; reading more would only cost memory and time. */
	static final int MAX_PAGES = 30;

	/** Upper bound on stored text, so one upload cannot fill the database. */
	static final int MAX_CHARACTERS = 200_000;

	@Override
	public String extractText(byte[] pdf) {
		try (PDDocument document = Loader.loadPDF(pdf)) {
			if (document.isEncrypted()) {
				throw new InvalidRequestException("Password-protected PDFs are not supported");
			}
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setEndPage(MAX_PAGES);
			String text = stripper.getText(document).strip();
			return text.length() > MAX_CHARACTERS ? text.substring(0, MAX_CHARACTERS) : text;
		}
		catch (IOException ex) {
			throw new InvalidRequestException("The file is not a readable PDF");
		}
	}

}
