package com.applypilot.cv;

/**
 * Turns a PDF into plain text. An interface so the service can be unit-tested without real
 * PDF parsing, and so the library behind it can change.
 */
public interface PdfTextExtractor {

	/**
	 * @return the document's text, or an empty string when it has no text layer (a scan)
	 * @throws com.applypilot.common.error.InvalidRequestException if the bytes are not a
	 * readable PDF
	 */
	String extractText(byte[] pdf);

}
