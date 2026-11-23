package com.applypilot.agent;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Puts untrusted text (a job posting, a CV, a letter the model wrote) into a prompt so that
 * it cannot pretend to be our instructions.
 *
 * <p>Each piece is wrapped in a tag named after what it is, and every tag we use is removed
 * from the text first, in any case and spacing, repeatedly: removing {@code </cv>} from
 * {@code </c</cv>v>} would otherwise leave a new, valid tag behind. A CV therefore cannot
 * close its own wrapper, nor open a fake "reviewer feedback" section.
 */
public final class UntrustedText {

	static final List<String> TAGS = List.of("job_posting", "job_requirements", "cv", "cv_excerpt", "cover_letter",
			"previous_letter", "reviewer_feedback", "match_result");

	private static final Pattern OUR_TAGS = Pattern
		.compile("(?i)<\\s*/?\\s*(?:" + String.join("|", TAGS) + ")\\b[^>]*>");

	private UntrustedText() {
	}

	/** The text without any of our tags and without NUL characters. */
	public static String clean(String text) {
		if (text == null) {
			return "";
		}
		String safe = text.replace("\u0000", "");
		String previous;
		do {
			previous = safe;
			safe = OUR_TAGS.matcher(previous).replaceAll("");
		}
		while (!safe.equals(previous));
		return safe;
	}

	/** {@code <tag>text</tag>} with the text cleaned. */
	public static String wrap(String tag, String text) {
		if (!TAGS.contains(tag)) {
			throw new IllegalArgumentException("Unknown tag: " + tag);
		}
		return "<" + tag + ">\n" + clean(text).strip() + "\n</" + tag + ">";
	}

	/** At most {@code maxChars} characters, saying so when something was cut. */
	public static String truncate(String text, int maxChars) {
		if (text.length() <= maxChars) {
			return text;
		}
		return text.substring(0, maxChars) + "\n[... cut: the original has " + text.length() + " characters]";
	}

}
