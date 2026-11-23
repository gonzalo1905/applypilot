package com.applypilot.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.applypilot.application.WorkMode;
import org.junit.jupiter.api.Test;

class KeywordJobPostingExtractorTest {

	private final KeywordJobPostingExtractor extractor = new KeywordJobPostingExtractor();

	@Test
	void splitsSkillsIntoRequiredAndNiceToHaveAtTheFirstOptionalHeading() {
		ExtractedPosting posting = extract("""
				Senior Java Developer
				We need Java 21, Spring Boot and PostgreSQL. Docker is used daily.

				Nice to have:
				- Kubernetes
				- Angular
				""");

		assertThat(posting.requiredSkills()).containsExactly("Java", "Spring Boot", "PostgreSQL", "Docker");
		assertThat(posting.niceToHaveSkills()).containsExactlyInAnyOrder("Kubernetes", "Angular");
		assertThat(posting.seniority()).isEqualTo(Seniority.SENIOR);
	}

	@Test
	void matchesWholeWordsOnlyAndSkillNamesWithSymbols() {
		ExtractedPosting posting = extract("Our stack: JavaScript, C++, C# and .NET. Some Javaland meetups.");

		assertThat(posting.requiredSkills()).containsExactlyInAnyOrder("JavaScript", "C#", "C++", ".NET")
			.doesNotContain("Java");
	}

	@Test
	void recognizesAlternativeSpellingsAndSpanishPostings() {
		ExtractedPosting posting = extract("""
				Desarrollador semi senior. Modalidad híbrida en Lima.
				Requisitos: Postgres, NodeJS, inglés intermedio y español nativo.
				Deseable: K8s.
				Sueldo: S/ 6000 - S/ 8000 mensuales.
				""");

		assertThat(posting.requiredSkills()).containsExactly("Node.js", "PostgreSQL");
		assertThat(posting.niceToHaveSkills()).containsExactly("Kubernetes");
		// "semi senior" starts before the "senior" inside it, so the longer phrase wins.
		assertThat(posting.seniority()).isEqualTo(Seniority.MID);
		assertThat(posting.workMode()).isEqualTo(WorkMode.HYBRID);
		assertThat(posting.languages()).containsExactly("English", "Spanish");
		assertThat(posting.salary()).isEqualTo("S/ 6000 - S/ 8000");
	}

	@Test
	void hybridWinsOverRemoteBecauseHybridPostingsMentionBoth() {
		assertThat(extract("Hybrid role: two days in the office, the rest remote.").workMode())
			.isEqualTo(WorkMode.HYBRID);
		assertThat(extract("Fully remote position for a backend engineer.").workMode()).isEqualTo(WorkMode.REMOTE);
	}

	@Test
	void leavesWhatItCannotDetectEmptyInsteadOfGuessing() {
		ExtractedPosting posting = extract("We are looking for a great person to join our friendly team.");

		assertThat(posting.company()).isNull();
		assertThat(posting.roleTitle()).isNull();
		assertThat(posting.location()).isNull();
		assertThat(posting.workMode()).isNull();
		assertThat(posting.salary()).isNull();
		assertThat(posting.seniority()).isEqualTo(Seniority.UNSPECIFIED);
		assertThat(posting.requiredSkills()).isEmpty();
		assertThat(posting.niceToHaveSkills()).isEmpty();
		assertThat(posting.languages()).isEmpty();
		assertThat(posting.summary()).startsWith("Keyword-based extraction");
	}

	@Test
	void isDeterministicAndReportsNoModel() {
		String text = "Junior Python developer, remote, Django and AWS.";

		JobPostingExtractor.Extraction first = extractor.extract(text);

		assertThat(extractor.extract(text)).isEqualTo(first);
		assertThat(first.model()).isNull();
	}

	private ExtractedPosting extract(String text) {
		return extractor.extract(text).posting();
	}

}
