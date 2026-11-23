package com.applypilot.agent;

import static com.applypilot.agent.AgentTestSupport.CV;
import static org.assertj.core.api.Assertions.assertThat;

import com.applypilot.agent.MatchResult.MatchedSkill;
import com.applypilot.agent.MatchResult.MissingSkill;
import com.applypilot.agent.MatcherAnswer.SkillVerdict;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchScoringTest {

	private static final String NORMALIZED_CV = MatchScoring.normalize(CV);

	@Test
	void requiredSkillsWeighTwoAndNiceToHaveSkillsOne() {
		MatchResult result = MatchScoring.score(List.of("Java", "Kafka"), List.of("Docker"),
				List.of(match("Java", "6 years of Java experience"), match("Docker", "Docker on AWS")), "Summary.",
				CV);

		// (2 + 1) of (2 + 2 + 1) points
		assertThat(result.score()).isEqualTo(60);
		assertThat(result.matchedSkills()).containsExactly(new MatchedSkill("Java", true, "6 years of Java experience"),
				new MatchedSkill("Docker", false, "Docker on AWS"));
		assertThat(result.missingSkills()).containsExactly(new MissingSkill("Kafka", true));
		assertThat(result.summary()).isEqualTo("Summary.");
		assertThat(result.rejectedEvidence()).isZero();
	}

	@Test
	void roundsTheScoreToTheNearestInteger() {
		List<String> required = List.of("Java", "Kafka", "Rust");

		assertThat(score(required, List.of(), List.of(match("Java", "Java experience")))).isEqualTo(33);
		assertThat(score(required, List.of(),
				List.of(match("Java", "Java experience"), match("Kafka", "Spring Boot")))).isEqualTo(67);
		// 1 of 8 points is 12.5, rounded half up
		assertThat(score(required, List.of("Docker", "Go"), List.of(match("Docker", "Docker on AWS"))))
			.isEqualTo(13);
	}

	@Test
	void aSkillInBothListsCountsOnceAsRequired() {
		MatchResult result = MatchScoring.score(List.of("Java"), List.of("java", "Docker"),
				List.of(match("Java", "Java experience")), null, CV);

		// Java (2 of 2) plus Docker (0 of 1)
		assertThat(result.score()).isEqualTo(67);
		assertThat(result.matchedSkills()).containsExactly(new MatchedSkill("Java", true, "Java experience"));
		assertThat(result.missingSkills()).containsExactly(new MissingSkill("Docker", false));
	}

	@Test
	void verdictsAreFoundIgnoringCaseAndSurroundingSpaces() {
		MatchResult result = MatchScoring.score(List.of("Spring Boot"), List.of(),
				List.of(match("  spring BOOT ", "Spring Boot")), null, CV);

		assertThat(result.score()).isEqualTo(100);
	}

	@Test
	void aMatchWhoseQuoteIsNotInTheCvIsRejected() {
		MatchResult result = MatchScoring.score(List.of("Kafka"), List.of(),
				List.of(match("Kafka", "Built event pipelines with Kafka")), null, CV);

		assertThat(result.score()).isZero();
		assertThat(result.matchedSkills()).isEmpty();
		assertThat(result.missingSkills()).containsExactly(new MissingSkill("Kafka", true));
		assertThat(result.rejectedEvidence()).isEqualTo(1);
	}

	@Test
	void aMatchWithoutEvidenceIsRejected() {
		MatchResult result = MatchScoring.score(List.of("Java"), List.of(), List.of(match("Java", null)), null, CV);

		assertThat(result.matchedSkills()).isEmpty();
		assertThat(result.rejectedEvidence()).isEqualTo(1);
	}

	@Test
	void unmatchedAndMissingVerdictsAreMissingButNotRejected() {
		MatchResult result = MatchScoring.score(List.of("Java", "Kafka"), List.of(),
				List.of(new SkillVerdict("Java", false, null)), null, CV);

		assertThat(result.missingSkills()).containsExactly(new MissingSkill("Java", true),
				new MissingSkill("Kafka", true));
		assertThat(result.rejectedEvidence()).isZero();
	}

	@Test
	void verdictsForSkillsNotInThePostingAreIgnored() {
		MatchResult result = MatchScoring.score(List.of("Kafka"), List.of(),
				List.of(match("Git", "Git"), match("Java", "Java experience")), null, CV);

		assertThat(result.score()).isZero();
		assertThat(result.matchedSkills()).isEmpty();
		assertThat(result.missingSkills()).containsExactly(new MissingSkill("Kafka", true));
	}

	@Test
	void aPostingWithoutSkillsScoresZero() {
		assertThat(score(List.of(), List.of(), List.of(match("Java", "Java experience")))).isZero();
	}

	@Test
	void quotesAreFoundIgnoringCaseWhitespaceAndQuoteMarks() {
		assertThat(MatchScoring.isQuoteOf("6 YEARS of java EXPERIENCE", NORMALIZED_CV)).isTrue();
		assertThat(MatchScoring.isQuoteOf("built REST APIs\n   with  Spring\tBoot", NORMALIZED_CV)).isTrue();
		assertThat(MatchScoring.isQuoteOf("  \"Docker on AWS.\"  ", NORMALIZED_CV)).isTrue();
		assertThat(MatchScoring.isQuoteOf("“Deployed services”…", NORMALIZED_CV)).isTrue();
		assertThat(MatchScoring.isQuoteOf("'Spanish (native)'", NORMALIZED_CV)).isTrue();
	}

	@Test
	void quotesThatAreNotInTheCvAreRejected() {
		assertThat(MatchScoring.isQuoteOf("10 years of Java experience", NORMALIZED_CV)).isFalse();
		assertThat(MatchScoring.isQuoteOf(null, NORMALIZED_CV)).isFalse();
	}

	@Test
	void quotesShorterThanThreeCharactersAreRejected() {
		// "Gi" and "AW" are in the CV, but too short to prove anything.
		assertThat(MatchScoring.isQuoteOf("Gi", NORMALIZED_CV)).isFalse();
		assertThat(MatchScoring.isQuoteOf("\"AW\"", NORMALIZED_CV)).isFalse();
		assertThat(MatchScoring.isQuoteOf("\"   \"", NORMALIZED_CV)).isFalse();
		assertThat(MatchScoring.isQuoteOf("AWS", NORMALIZED_CV)).isTrue();
	}

	private static int score(List<String> required, List<String> niceToHave, List<SkillVerdict> verdicts) {
		return MatchScoring.score(required, niceToHave, verdicts, null, CV).score();
	}

	private static SkillVerdict match(String skill, String evidence) {
		return new SkillVerdict(skill, true, evidence);
	}

}
