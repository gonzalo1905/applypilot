package com.applypilot.analysis;

import com.applypilot.application.WorkMode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Extracts what it can from a posting without any AI: it looks for known skill names (a
 * dictionary file) and a few fixed phrases. Used when the language model fails or is not
 * configured.
 *
 * <p>It is deterministic (the same text always gives the same result), fast, free and cannot
 * fail, which is exactly what a fallback needs. It is also much less capable: it cannot find
 * the company or the job title, and it only knows the skills in its dictionary. Fields it
 * cannot determine stay empty rather than guessed.
 */
@Component
public class KeywordJobPostingExtractor implements JobPostingExtractor {

	private static final String DICTIONARY = "analysis/skills-dictionary.txt";

	private static final int MAX_SKILLS = 40;

	/** Skill names contain symbols (C++, C#, .NET), so "word boundary" is defined by hand. */
	private static final String NOT_PART_OF_A_WORD_BEFORE = "(?<![\\p{L}\\p{N}+#])";

	private static final String NOT_PART_OF_A_WORD_AFTER = "(?![\\p{L}\\p{N}+#])";

	private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

	/** Headings that start the "optional" part of a posting, in English and Spanish. */
	private static final Pattern NICE_TO_HAVE_HEADING = Pattern.compile(
			"nice[ -]to[ -]have|preferred qualifications|bonus points|is a plus|are a plus|would be a plus"
					+ "|deseable|deseables|valorable|se valorar[aá]|ser[aá] un plus|opcional",
			FLAGS);

	private static final Pattern SALARY = Pattern.compile(
			"(?:USD|US\\$|\\$|S/\\.?|PEN|EUR|€|£)\\s?\\d[\\d.,]*\\s?[kK]?"
					+ "(?:\\s?(?:-|–|to|a|y)\\s?(?:USD|US\\$|\\$|S/\\.?|PEN|EUR|€|£)?\\s?\\d[\\d.,]*\\s?[kK]?)?");

	private final List<Skill> skills;

	/** The level mentioned first wins: postings open with the title ("Senior Java Developer"). */
	private final List<Rule<Seniority>> seniorityRules = List.of(
			rule(Seniority.INTERN, "intern", "internship", "trainee", "practicante", "pr[aá]cticas", "becario"),
			rule(Seniority.MID, "semi[ -]?senior", "ssr", "mid[ -]level", "mid[ -]senior", "intermediate"),
			rule(Seniority.LEAD, "tech lead", "technical lead", "team lead", "staff engineer",
					"l[ií]der t[eé]cnico"),
			rule(Seniority.SENIOR, "senior", "sr\\.?"), rule(Seniority.JUNIOR, "junior", "jr\\.?", "entry[ -]level"));

	private final List<Rule<WorkMode>> workModeRules = List.of(
			// Hybrid first: hybrid postings usually mention both "remote" and "office".
			rule(WorkMode.HYBRID, "hybrid", "h[ií]brido", "h[ií]brida"),
			rule(WorkMode.REMOTE, "remote", "remoto", "remota", "teletrabajo", "work from home", "home office"),
			rule(WorkMode.ONSITE, "on[ -]?site", "in[ -]office", "presencial"));

	private final List<Rule<String>> languageRules = List.of(rule("English", "english", "ingl[eé]s"),
			rule("Spanish", "spanish", "espa[nñ]ol", "castellano"), rule("Portuguese", "portuguese", "portugu[eé]s"),
			rule("French", "french", "franc[eé]s"), rule("German", "german", "alem[aá]n"),
			rule("Italian", "italian", "italiano"));

	public KeywordJobPostingExtractor() {
		this.skills = loadDictionary();
	}

	@Override
	public Extraction extract(String postingText) {
		// Everything after the first "nice to have" style heading counts as optional.
		Matcher heading = NICE_TO_HAVE_HEADING.matcher(postingText);
		int optionalFrom = heading.find() ? heading.start() : postingText.length();

		List<String> required = new ArrayList<>();
		List<String> niceToHave = new ArrayList<>();
		for (Skill skill : skills) {
			Matcher matcher = skill.pattern().matcher(postingText);
			if (matcher.find()) {
				List<String> target = matcher.start() < optionalFrom ? required : niceToHave;
				if (target.size() < MAX_SKILLS) {
					target.add(skill.name());
				}
			}
		}

		List<String> languages = languageRules.stream()
			.filter(rule -> rule.pattern().matcher(postingText).find())
			.map(Rule::value)
			.toList();

		String summary = "Keyword-based extraction: the AI model was not available, so only skills from a "
				+ "built-in list were detected (" + required.size() + " required, " + niceToHave.size()
				+ " nice to have). Company and job title are not detected in this mode.";

		ExtractedPosting posting = new ExtractedPosting(null, null,
				earliestMatch(seniorityRules, postingText, Seniority.UNSPECIFIED), required, niceToHave,
				firstMatch(workModeRules, postingText, null), null, languages, salary(postingText), summary);
		return new Extraction(posting, null);
	}

	private static String salary(String postingText) {
		Matcher matcher = SALARY.matcher(postingText);
		if (!matcher.find()) {
			return null;
		}
		String salary = matcher.group().strip();
		return salary.length() > 160 ? salary.substring(0, 160) : salary;
	}

	/**
	 * The value whose phrase appears earliest in the text. "Semi senior" starts before the
	 * "senior" inside it, so the longer phrase wins.
	 */
	private static <T> T earliestMatch(List<Rule<T>> rules, String text, T defaultValue) {
		T best = defaultValue;
		int bestStart = Integer.MAX_VALUE;
		for (Rule<T> rule : rules) {
			Matcher matcher = rule.pattern().matcher(text);
			if (matcher.find() && matcher.start() < bestStart) {
				best = rule.value();
				bestStart = matcher.start();
			}
		}
		return best;
	}

	/** The first rule, in list order, whose phrase appears anywhere in the text. */
	private static <T> T firstMatch(List<Rule<T>> rules, String text, T defaultValue) {
		for (Rule<T> rule : rules) {
			if (rule.pattern().matcher(text).find()) {
				return rule.value();
			}
		}
		return defaultValue;
	}

	private static <T> Rule<T> rule(T value, String... alternatives) {
		return new Rule<>(value, Pattern.compile(
				NOT_PART_OF_A_WORD_BEFORE + "(?:" + String.join("|", alternatives) + ")" + NOT_PART_OF_A_WORD_AFTER,
				FLAGS));
	}

	private static List<Skill> loadDictionary() {
		String content;
		try {
			content = new ClassPathResource(DICTIONARY).getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not read the skills dictionary", ex);
		}
		Map<String, Skill> byName = new LinkedHashMap<>();
		for (String line : content.split("\\R")) {
			String trimmed = line.strip();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) {
				continue;
			}
			String[] spellings = trimmed.split("\\|");
			List<String> quoted = new ArrayList<>();
			for (String spelling : spellings) {
				if (!spelling.isBlank()) {
					// Spaces in a name also match any run of spaces, hyphens or line breaks.
					quoted.add(Pattern.quote(spelling.strip()).replace(" ", "\\E[\\s-]+\\Q"));
				}
			}
			String name = spellings[0].strip();
			byName.putIfAbsent(name, new Skill(name, Pattern.compile(
					NOT_PART_OF_A_WORD_BEFORE + "(?:" + String.join("|", quoted) + ")" + NOT_PART_OF_A_WORD_AFTER,
					FLAGS)));
		}
		return List.copyOf(byName.values());
	}

	private record Skill(String name, Pattern pattern) {
	}

	private record Rule<T>(T value, Pattern pattern) {
	}

}
