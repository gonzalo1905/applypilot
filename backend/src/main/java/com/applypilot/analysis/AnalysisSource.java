package com.applypilot.analysis;

/**
 * Who produced an analysis.
 */
public enum AnalysisSource {

	/** The language model, with its output validated against our schema. */
	LLM,

	/** The deterministic keyword extractor, used when the model could not be used. */
	FALLBACK

}
