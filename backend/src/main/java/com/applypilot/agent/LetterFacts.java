package com.applypilot.agent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Facts in a letter that code can check exactly. The first eval run found invented
 * percentages ("reduced costs by 30%") in 7 of 12 first drafts; a number the CV does not
 * contain is the most common invented fact, and the cheapest to catch.
 */
public final class LetterFacts {

	/** Numbers as people write them: 7, 35%, 2,000, 4.5. */
	private static final Pattern NUMBERS = Pattern.compile("\\d+(?:[.,]\\d+)*");

	/** A thousands separator: "2,000" and "2.000" are 2000, but "4.5" stays 4.5. */
	private static final Pattern THOUSANDS = Pattern.compile("[.,](?=\\d{3}(?:\\D|$))");

	private LetterFacts() {
	}

	/**
	 * Numbers in the letter that the CV does not contain, in order of appearance. Numbers
	 * written as words ("six years") are not seen.
	 */
	public static List<String> numbersNotIn(String letter, String cv) {
		Set<String> cvNumbers = new LinkedHashSet<>(numbers(cv));
		List<String> unknown = new ArrayList<>();
		for (String number : numbers(letter)) {
			if (!cvNumbers.contains(number) && !unknown.contains(number)) {
				unknown.add(number);
			}
		}
		return unknown;
	}

	private static List<String> numbers(String text) {
		List<String> found = new ArrayList<>();
		Matcher matcher = NUMBERS.matcher(text == null ? "" : text);
		while (matcher.find()) {
			// "2,000" and "2.000" are the same number; "4.5" and "4,5" are the same decimal.
			found.add(THOUSANDS.matcher(matcher.group()).replaceAll("").replace(',', '.'));
		}
		return found;
	}

}
