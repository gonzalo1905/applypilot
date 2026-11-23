package com.applypilot.agent;

import com.applypilot.analysis.ExtractedPosting;
import com.applypilot.analysis.Seniority;
import com.applypilot.application.WorkMode;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;

/** Shared fixtures for agent unit tests. */
final class AgentTestSupport {

	static final JsonMapper JSON = JsonMapper.builder().build();

	static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	static final JsonAnswers ANSWERS = new JsonAnswers(JSON, VALIDATOR);

	static final String CV = """
			Ana Torres - Backend Developer
			Lima, Peru. ana@example.com
			Summary: Backend developer with 6 years of Java experience.
			Experience
			Acme Payments (2021-2025): built REST APIs with Spring Boot and PostgreSQL.
			Deployed services with Docker on AWS.
			Skills: Java, Spring Boot, PostgreSQL, Docker, Git
			Languages: Spanish (native), English (B2)
			""";

	static final ExtractedPosting POSTING = new ExtractedPosting("Acme", "Senior Backend Engineer", Seniority.SENIOR,
			List.of("Java", "Spring Boot", "Kafka"), List.of("Docker"), WorkMode.REMOTE, "Lima, Peru",
			List.of("English"), null, "Backend role building REST APIs.");

	private AgentTestSupport() {
	}

}
