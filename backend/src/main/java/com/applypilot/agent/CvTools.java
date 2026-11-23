package com.applypilot.agent;

import com.applypilot.common.ai.ToolCall;
import com.applypilot.common.ai.ToolSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The tools the Matcher model may ask for, run by our code over one CV.
 *
 * <p>Ownership is decided before the model is involved: the orchestrator loads the CV of the
 * run's user (the id comes from the JWT) and builds these tools over that text only. No tool
 * takes a user or document id, so nothing the model writes can point it at another user's
 * data. Arguments are untrusted and validated; results are excerpts of the CV, wrapped as
 * untrusted text. A tool never throws: a bad request becomes an error message the model can
 * read and correct.
 */
public final class CvTools {

	static final String PROFILE = "getCandidateProfile";

	static final String SEARCH = "searchCv";

	static final ToolSpec PROFILE_SPEC = new ToolSpec(PROFILE,
			"Returns the beginning of the candidate's CV (headline, summary, latest roles) and its total length.",
			"""
					{"type": "object", "properties": {}, "additionalProperties": false}""");

	static final ToolSpec SEARCH_SPEC = new ToolSpec(SEARCH,
			"Returns the CV lines that contain a term (case-insensitive), at most 5. Use short terms such as "
					+ "a technology name; try a synonym if nothing is found.",
			"""
					{"type": "object",
					 "properties": {"term": {"type": "string", "description": "Text to look for, 2 to 60 characters."}},
					 "required": ["term"], "additionalProperties": false}""");

	static final List<ToolSpec> SPECS = List.of(PROFILE_SPEC, SEARCH_SPEC);

	static final int PROFILE_CHARS = 1500;

	static final int MAX_RESULTS = 5;

	static final int MAX_LINE_CHARS = 300;

	private final String cvText;

	private final JsonMapper jsonMapper;

	public CvTools(String cvText, JsonMapper jsonMapper) {
		this.cvText = cvText == null ? "" : cvText;
		this.jsonMapper = jsonMapper;
	}

	/** Runs one tool call and returns the text to send back to the model. */
	public String execute(ToolCall call) {
		if (PROFILE.equals(call.name())) {
			return profile();
		}
		if (SEARCH.equals(call.name())) {
			String term = searchTerm(call.arguments());
			return term == null ? "Error: searchCv needs {\"term\": \"...\"} with 2 to 60 characters." : search(term);
		}
		return "Error: unknown tool. Available tools: " + PROFILE + ", " + SEARCH + ".";
	}

	String profile() {
		String start = cvText.length() <= PROFILE_CHARS ? cvText : cvText.substring(0, PROFILE_CHARS);
		return UntrustedText.wrap("cv_excerpt", start) + "\nThe CV has " + cvText.length()
				+ " characters in total. Use searchCv to find specific facts.";
	}

	/** Every CV line containing the term, as a window around the match when the line is long. */
	public List<String> searchLines(String term) {
		String needle = term.toLowerCase(Locale.ROOT);
		List<String> results = new ArrayList<>();
		for (String line : cvText.split("\\R")) {
			String compact = line.strip().replaceAll("\\s+", " ");
			int index = compact.toLowerCase(Locale.ROOT).indexOf(needle);
			if (index >= 0) {
				results.add(window(compact, index));
				if (results.size() == MAX_RESULTS) {
					break;
				}
			}
		}
		return results;
	}

	String search(String term) {
		List<String> lines = searchLines(term);
		if (lines.isEmpty()) {
			return "No line of the CV contains \"" + UntrustedText.clean(term) + "\".";
		}
		return UntrustedText.wrap("cv_excerpt", "- " + String.join("\n- ", lines));
	}

	private String searchTerm(String arguments) {
		SearchArguments parsed;
		try {
			parsed = jsonMapper.readerFor(SearchArguments.class)
				.without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
				.readValue(arguments == null || arguments.isBlank() ? "{}" : arguments);
		}
		catch (JacksonException ex) {
			return null;
		}
		if (parsed == null || parsed.term() == null) {
			return null;
		}
		String term = parsed.term().strip().replaceAll("\\s+", " ");
		return term.length() < 2 || term.length() > 60 ? null : term;
	}

	private static String window(String line, int matchIndex) {
		if (line.length() <= MAX_LINE_CHARS) {
			return line;
		}
		int start = Math.max(0, matchIndex - 120);
		int end = Math.min(line.length(), start + MAX_LINE_CHARS);
		return (start > 0 ? "..." : "") + line.substring(start, end) + (end < line.length() ? "..." : "");
	}

	private record SearchArguments(String term) {
	}

}
