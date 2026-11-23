package com.applypilot.agent;

import static com.applypilot.agent.AgentTestSupport.CV;
import static com.applypilot.agent.AgentTestSupport.POSTING;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KeywordSkillMatcherTest {

	private final KeywordSkillMatcher matcher = new KeywordSkillMatcher();

	@Test
	void findsSkillsByNameAndQuotesTheCvLine() {
		MatchResult result = matcher.match(POSTING, CV);

		assertThat(result.matchedSkills()).extracting(MatchResult.MatchedSkill::skill)
			.containsExactly("Java", "Spring Boot", "Docker");
		assertThat(result.matchedSkills().get(0).evidence()).contains("Java");
		assertThat(result.missingSkills()).containsExactly(new MatchResult.MissingSkill("Kafka", true));
		assertThat(result.score()).isEqualTo(71);
		assertThat(result.summary()).startsWith("Keyword comparison without AI: 2 of 3 required skills");
	}

	@Test
	void matchesWholeWordsOnlyAndHandlesSymbols() {
		assertThat(KeywordSkillMatcher.findLine("Java", "Worked with JavaScript only")).isNull();
		assertThat(KeywordSkillMatcher.findLine("C++", "Languages: C++, Rust")).isEqualTo("Languages: C++, Rust");
		assertThat(KeywordSkillMatcher.findLine("go", "Go and Python")).isEqualTo("Go and Python");
	}

	@Test
	void aPostingWithoutSkillsScoresZero() {
		MatchResult result = matcher.match(new com.applypilot.analysis.ExtractedPosting(null, null,
				com.applypilot.analysis.Seniority.UNSPECIFIED, java.util.List.of(), java.util.List.of(), null, null,
				java.util.List.of(), null, "Nothing."), CV);

		assertThat(result.score()).isZero();
		assertThat(result.matchedSkills()).isEmpty();
	}

}
