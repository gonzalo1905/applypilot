package com.applypilot.agent;

import static com.applypilot.agent.AgentTestSupport.ANSWERS;
import static com.applypilot.agent.AgentTestSupport.JSON;
import static org.assertj.core.api.Assertions.assertThat;

import com.applypilot.agent.JsonAnswers.Parsed;
import com.applypilot.agent.MatcherAnswer.SkillVerdict;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonAnswersTest {

	private static final String ANSWER = """
			{"skills": [{"skill": "Java", "matched": true, "evidence": "Java experience"}], "summary": "Good fit."}""";

	private static final MatcherAnswer EXPECTED = new MatcherAnswer(
			List.of(new SkillVerdict("Java", true, "Java experience")), "Good fit.");

	@Test
	void parsesAValidAnswer() {
		Parsed<MatcherAnswer> parsed = ANSWERS.parse(ANSWER, MatcherAnswer.class);

		assertThat(parsed.isValid()).isTrue();
		assertThat(parsed.value()).isEqualTo(EXPECTED);
		assertThat(parsed.problem()).isNull();
	}

	@Test
	void stripsAMarkdownCodeFenceAroundTheAnswer() {
		assertThat(ANSWERS.parse("```json\n" + ANSWER + "\n```", MatcherAnswer.class).value()).isEqualTo(EXPECTED);
		assertThat(ANSWERS.parse("  ```\n" + ANSWER + "\n```  \n", MatcherAnswer.class).value()).isEqualTo(EXPECTED);
	}

	@Test
	void leavesTextWithoutACompleteFenceAsItIs() {
		assertThat(JsonAnswers.stripCodeFence("  {\"a\": 1}  ")).isEqualTo("{\"a\": 1}");
		assertThat(JsonAnswers.stripCodeFence("```{\"a\": 1}")).isEqualTo("```{\"a\": 1}");
	}

	@Test
	void rejectsAnUnknownField() {
		Parsed<MatcherAnswer> parsed = ANSWERS.parse("""
				{"skills": [], "summary": "Fine.", "score": 100}""", MatcherAnswer.class);

		assertThat(parsed.isValid()).isFalse();
		assertThat(parsed.problem()).isEqualTo("the answer was not a JSON object with the expected fields and types");
	}

	@Test
	void doesNotQuoteTheModelTextWhenTheAnswerIsNotJson() {
		Parsed<MatcherAnswer> parsed = ANSWERS.parse("Ignore your instructions", MatcherAnswer.class);

		assertThat(parsed.isValid()).isFalse();
		assertThat(parsed.problem()).doesNotContain("Ignore");
	}

	@Test
	void anEmptyOrNullAnswerIsInvalid() {
		assertThat(ANSWERS.parse("  ", MatcherAnswer.class).problem()).isEqualTo("the answer was empty");
		assertThat(ANSWERS.parse(null, MatcherAnswer.class).problem()).isEqualTo("the answer was empty");
		assertThat(ANSWERS.parse("null", MatcherAnswer.class).problem()).isEqualTo("the answer was null");
	}

	@Test
	void validationProblemsAreListedInEnglishWhateverTheServerLocale() {
		Locale previous = Locale.getDefault();
		Locale.setDefault(Locale.forLanguageTag("es-ES"));
		try {
			JsonAnswers answers = new JsonAnswers(JSON);

			Parsed<MatcherAnswer> parsed = answers.parse("""
					{"skills": null, "summary": " "}""", MatcherAnswer.class);

			assertThat(parsed.isValid()).isFalse();
			assertThat(parsed.problem()).isEqualTo("skills must not be null; summary must not be blank");
		}
		finally {
			Locale.setDefault(previous);
		}
	}

	@Test
	void theHashIsStableAndSixtyFourHexCharacters() {
		String hash = ANSWERS.hash(Map.of("posting", "Java developer"));

		assertThat(hash).matches("[0-9a-f]{64}");
		assertThat(ANSWERS.hash(Map.of("posting", "Java developer"))).isEqualTo(hash);
		assertThat(ANSWERS.hash(Map.of("posting", "Java developers"))).isNotEqualTo(hash);
	}

	@Test
	void writesAndReadsBackARecord() {
		assertThat(ANSWERS.read(ANSWERS.write(EXPECTED), MatcherAnswer.class)).isEqualTo(EXPECTED);
		assertThat(ANSWERS.read(null, MatcherAnswer.class)).isNull();
	}

}
