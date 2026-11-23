package com.applypilot.agent.evals;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The eval checks run in every build, so a broken metric cannot hide a regression. */
class EvalMetricsTest {

	@Test
	void comparesSkillNamesLooselyButNotCarelessly() {
		assertThat(EvalMetrics.sameSkill("Java 21", "Java")).isTrue();
		assertThat(EvalMetrics.sameSkill("REST APIs", "APIs REST")).isTrue();
		assertThat(EvalMetrics.sameSkill("Node.js", "NodeJS")).isTrue();
		assertThat(EvalMetrics.sameSkill("CI/CD pipelines", "CI/CD")).isTrue();
		assertThat(EvalMetrics.sameSkill("C#", "C")).isFalse();
		assertThat(EvalMetrics.sameSkill("JavaScript", "Java")).isFalse();
		assertThat(EvalMetrics.sameSkill("Spring Boot", "Spring")).isTrue();
	}

	@Test
	void computesPrecisionAndRecallWithTheMisses() {
		EvalMetrics.PrecisionRecall score = EvalMetrics.precisionRecall(List.of("Java", "Spring Boot", "COBOL"),
				List.of("Java", "Spring Boot", "Kafka", "Docker"));

		assertThat(score.precision()).isEqualTo(2 / 3.0);
		assertThat(score.recall()).isEqualTo(0.5);
		assertThat(score.falsePositives()).containsExactly("COBOL");
		assertThat(score.falseNegatives()).containsExactly("Kafka", "Docker");
	}

	@Test
	void anEmptyPredictionForAnEmptyLabelIsPerfect() {
		EvalMetrics.PrecisionRecall score = EvalMetrics.precisionRecall(List.of(), List.of());

		assertThat(score.precision()).isEqualTo(1);
		assertThat(score.recall()).isEqualTo(1);
	}

	@Test
	void findsNumbersTheCvDoesNotContain() {
		String cv = "7 years of Java. Cut batch time by 35%. 2,000 users. 2018-2020";

		assertThat(EvalMetrics.numbersNotIn("With 7 years and 35% faster jobs for 2.000 users since 2018", cv))
			.isEmpty();
		assertThat(EvalMetrics.numbersNotIn("I have 15 years and led a team of 20 at 40%", cv))
			.containsExactly("15", "20", "40");
		// A decimal is not merged into a different whole number.
		assertThat(EvalMetrics.numbersNotIn("4.5 years", "45 users")).containsExactly("4.5");
		assertThat(EvalMetrics.numbersNotIn("4,5 años", "4.5 years")).isEmpty();
	}

	@Test
	void findsLeakedCanariesIgnoringCase() {
		assertThat(EvalMetrics.leaks("Company: hacked inc", List.of("HACKED", "COBOL"))).containsExactly("HACKED");
		assertThat(EvalMetrics.leaks(null, List.of("HACKED"))).isEmpty();
	}

	@Test
	void checksQuotesIgnoringSpacingAndQuoteMarks() {
		assertThat(EvalMetrics.containsQuote("I built   REST APIs\nwith Spring", "\"built REST APIs with Spring\""))
			.isTrue();
		assertThat(EvalMetrics.containsQuote("I built REST APIs", "led a team")).isFalse();
		assertThat(EvalMetrics.containsQuote("anything", "  ")).isFalse();
	}

}
