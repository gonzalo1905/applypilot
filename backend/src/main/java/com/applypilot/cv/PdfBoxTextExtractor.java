package com.applypilot.cv;

import com.applypilot.common.error.InvalidRequestException;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Reads text with Apache PDFBox.
 *
 * <p>Parsing untrusted PDFs is the riskiest thing this application does: a small file can
 * expand to a lot of memory while it is decoded. The limits here (pages, characters, and how
 * many files are parsed at once) bound the damage but do not remove it. A hardened deployment
 * would parse in a separate, memory-limited worker process.
 */
@Component
public class PdfBoxTextExtractor implements PdfTextExtractor {

	/** A CV is a few pages; reading more would only cost memory and time. */
	static final int MAX_PAGES = 30;

	/** Upper bound on stored text, so one upload cannot fill the database. */
	static final int MAX_CHARACTERS = 200_000;

	/** How many PDFs may be parsed at the same time; further uploads wait their turn. */
	private static final int MAX_CONCURRENT_EXTRACTIONS = 2;

	private final Semaphore permits = new Semaphore(MAX_CONCURRENT_EXTRACTIONS);

	@Override
	public String extractText(byte[] pdf) {
		permits.acquireUninterruptibly();
		try (PDDocument document = Loader.loadPDF(pdf)) {
			// Some PDFs open without a password but forbid copying their text.
			if (!document.getCurrentAccessPermission().canExtractContent()) {
				throw new InvalidRequestException("This PDF does not allow its text to be read");
			}
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setEndPage(MAX_PAGES);
			// PostgreSQL text columns cannot hold the NUL character, which some PDFs produce.
			String text = stripper.getText(document).replace("\u0000", "").strip();
			return text.length() > MAX_CHARACTERS ? text.substring(0, MAX_CHARACTERS) : text;
		}
		catch (InvalidRequestException ex) {
			throw ex;
		}
		catch (InvalidPasswordException ex) {
			throw new InvalidRequestException("Password-protected PDFs are not supported");
		}
		catch (IOException | RuntimeException ex) {
			// PDFBox reports malformed files with both checked and unchecked exceptions.
			throw new InvalidRequestException("The file is not a readable PDF");
		}
		finally {
			permits.release();
		}
	}

}
