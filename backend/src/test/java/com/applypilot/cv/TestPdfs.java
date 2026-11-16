package com.applypilot.cv;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Builds small real PDFs in memory, so tests do not need binary fixture files.
 */
final class TestPdfs {

	private TestPdfs() {
	}

	/** A one-page PDF containing the given line of text. */
	static byte[] withText(String text) {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PDPage page = new PDPage();
			document.addPage(page);
			try (PDPageContentStream content = new PDPageContentStream(document, page)) {
				content.beginText();
				content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
				content.newLineAtOffset(72, 700);
				content.showText(text);
				content.endText();
			}
			document.save(out);
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** A PDF with the given number of pages, each containing {@code textOnPage(pageNumber)}. */
	static byte[] withPages(int pages, java.util.function.IntFunction<String> textOnPage) {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			for (int number = 1; number <= pages; number++) {
				PDPage page = new PDPage();
				document.addPage(page);
				try (PDPageContentStream content = new PDPageContentStream(document, page)) {
					content.beginText();
					content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
					content.newLineAtOffset(72, 700);
					content.showText(textOnPage.apply(number));
					content.endText();
				}
			}
			document.save(out);
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/**
	 * An encrypted PDF. With a non-empty {@code userPassword} it cannot be opened without it;
	 * with an empty one it opens freely but applies the given permission on copying text.
	 */
	static byte[] encrypted(String text, String userPassword, boolean allowTextExtraction) {
		try (PDDocument document = Loader.loadPDF(withText(text));
				ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			AccessPermission permission = new AccessPermission();
			permission.setCanExtractContent(allowTextExtraction);
			permission.setCanExtractForAccessibility(allowTextExtraction);
			StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-secret", userPassword, permission);
			policy.setEncryptionKeyLength(128);
			document.protect(policy);
			document.save(out);
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** A valid PDF with one blank page and no text layer, like a scanned document. */
	static byte[] withoutText() {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			document.addPage(new PDPage());
			document.save(out);
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
