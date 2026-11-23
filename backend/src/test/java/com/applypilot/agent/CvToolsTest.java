package com.applypilot.agent;

import static com.applypilot.agent.AgentTestSupport.CV;
import static com.applypilot.agent.AgentTestSupport.JSON;
import static org.assertj.core.api.Assertions.assertThat;

import com.applypilot.common.ai.ToolCall;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CvToolsTest {

	private static final String TERM_ERROR = "Error: searchCv needs {\"term\": \"...\"} with 2 to 60 characters.";

	private final CvTools tools = new CvTools(CV, JSON);

	@Test
	void theProfileIsTheWholeShortCvWithItsLength() {
		String result = tools.execute(new ToolCall("1", CvTools.PROFILE, "{}"));

		assertThat(result).startsWith("<cv_excerpt>\n").contains("Ana Torres - Backend Developer", "Languages: Spanish")
			.endsWith("The CV has " + CV.length() + " characters in total. Use searchCv to find specific facts.");
	}

	@Test
	void theProfileCutsALongCvAndStillGivesTheTotalLength() {
		String longCv = "a".repeat(CvTools.PROFILE_CHARS) + "TAIL" + "b".repeat(500);

		String result = new CvTools(longCv, JSON).execute(new ToolCall("1", CvTools.PROFILE, null));

		assertThat(result).contains("a".repeat(CvTools.PROFILE_CHARS)).doesNotContain("TAIL").doesNotContain("b");
		assertThat(result).contains("The CV has " + longCv.length() + " characters in total.");
	}

	@Test
	void searchIgnoresCaseAndSpacing() {
		String result = tools.execute(new ToolCall("1", CvTools.SEARCH, "{\"term\": \"SPRING   boot\"}"));

		assertThat(result).startsWith("<cv_excerpt>")
			.contains("- Acme Payments (2021-2025): built REST APIs with Spring Boot and PostgreSQL.")
			.contains("- Skills: Java, Spring Boot, PostgreSQL, Docker, Git");
	}

	@Test
	void searchReturnsAtMostFiveLines() {
		String cv = IntStream.rangeClosed(1, 7).mapToObj(n -> "Java project " + n).collect(Collectors.joining("\n"));

		List<String> lines = new CvTools(cv, JSON).searchLines("java");

		assertThat(lines).containsExactly("Java project 1", "Java project 2", "Java project 3", "Java project 4",
				"Java project 5");
	}

	@Test
	void searchSaysSoWhenNothingMatches() {
		String result = tools.execute(new ToolCall("1", CvTools.SEARCH, "{\"term\": \"Kafka\"}"));

		assertThat(result).isEqualTo("No line of the CV contains \"Kafka\".");
	}

	@Test
	void aLongLineIsCutToAWindowAroundTheMatch() {
		String line = "x".repeat(300) + "Kafka" + "y".repeat(300);

		String result = new CvTools(line, JSON).searchLines("kafka").get(0);

		// The window starts 120 characters before the match and is 300 characters long.
		assertThat(result).isEqualTo("..." + "x".repeat(120) + "Kafka" + "y".repeat(175) + "...");
	}

	@Test
	void aLongLineMatchedNearItsStartIsCutOnlyAtTheEnd() {
		String line = "Kafka " + "z".repeat(400);

		String result = new CvTools(line, JSON).searchLines("kafka").get(0);

		assertThat(result).startsWith("Kafka ").endsWith("...").hasSize(CvTools.MAX_LINE_CHARS + 3);
	}

	@Test
	void aLineOfExactlyTheLimitIsNotCut() {
		String line = "Kafka" + "z".repeat(CvTools.MAX_LINE_CHARS - 5);

		assertThat(new CvTools(line, JSON).searchLines("kafka")).containsExactly(line);
	}

	@Test
	void acceptsTermsOfTwoAndSixtyCharacters() {
		assertThat(tools.execute(new ToolCall("1", CvTools.SEARCH, "{\"term\": \"AW\"}")))
			.contains("- Deployed services with Docker on AWS.");
		assertThat(tools.execute(new ToolCall("1", CvTools.SEARCH, "{\"term\": \"" + "q".repeat(60) + "\"}")))
			.startsWith("No line of the CV contains");
	}

	@Test
	void rejectsATermShorterThanTwoCharacters() {
		assertThat(tools.execute(new ToolCall("1", CvTools.SEARCH, "{\"term\": \" J \"}"))).isEqualTo(TERM_ERROR);
	}

	@Test
	void rejectsATermLongerThanSixtyCharacters() {
		assertThat(tools.execute(new ToolCall("1", CvTools.SEARCH, "{\"term\": \"" + "q".repeat(61) + "\"}")))
			.isEqualTo(TERM_ERROR);
	}

	@Test
	void rejectsAMissingTerm() {
		assertThat(tools.execute(new ToolCall("1", CvTools.SEARCH, "{}"))).isEqualTo(TERM_ERROR);
		assertThat(tools.execute(new ToolCall("1", CvTools.SEARCH, null))).isEqualTo(TERM_ERROR);
		assertThat(tools.execute(new ToolCall("1", CvTools.SEARCH, "{\"query\": \"Java\"}"))).isEqualTo(TERM_ERROR);
	}

	@Test
	void rejectsMalformedArguments() {
		assertThat(tools.execute(new ToolCall("1", CvTools.SEARCH, "{\"term\": "))).isEqualTo(TERM_ERROR);
		assertThat(tools.execute(new ToolCall("1", CvTools.SEARCH, "Java"))).isEqualTo(TERM_ERROR);
		assertThat(tools.execute(new ToolCall("1", CvTools.SEARCH, "[\"Java\"]"))).isEqualTo(TERM_ERROR);
	}

	@Test
	void anUnknownToolGetsTheListOfAvailableTools() {
		String result = tools.execute(new ToolCall("1", "readOtherUsersCv", "{\"userId\": \"x\"}"));

		assertThat(result).isEqualTo("Error: unknown tool. Available tools: getCandidateProfile, searchCv.");
	}

}
