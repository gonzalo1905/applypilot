package com.applypilot.cv.dto;

import com.applypilot.cv.Cv;
import java.time.Instant;
import java.util.UUID;

/**
 * CV metadata. The extracted text itself is not returned; clients only learn whether text
 * could be read (a scanned PDF has none) and how much.
 */
public record CvResponse(UUID id, String fileName, long sizeBytes, Instant uploadedAt, boolean textExtracted,
		int extractedCharacters) {

	public static CvResponse from(Cv cv) {
		int characters = cv.getExtractedText().length();
		return new CvResponse(cv.getId(), cv.getFileName(), cv.getSizeBytes(), cv.getUploadedAt(), characters > 0,
				characters);
	}

}
