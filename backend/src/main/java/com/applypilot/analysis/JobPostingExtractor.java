package com.applypilot.analysis;

/**
 * Turns the text of a job posting into structured data. Two implementations: one asks a
 * language model, the other only matches keywords and can never fail.
 */
public interface JobPostingExtractor {

	/**
	 * @param postingText the posting, already normalized and within the length limit
	 * @throws ExtractionFailedException if this extractor could not produce a valid result
	 */
	Extraction extract(String postingText);

	/**
	 * @param posting the validated result
	 * @param model the model that produced it; null for the keyword extractor
	 */
	record Extraction(ExtractedPosting posting, String model) {
	}

}
