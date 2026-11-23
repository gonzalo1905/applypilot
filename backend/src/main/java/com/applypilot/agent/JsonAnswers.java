package com.applypilot.agent;

import com.applypilot.common.ai.ModelOutputValidation;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns a model's answer into a validated record, or explains what was wrong with it, and
 * writes the records the orchestrator stores. Never trusts the answer: strict parsing (an
 * unknown field is an error) and Bean Validation on the result.
 */
@Component
public class JsonAnswers {

	private final JsonMapper jsonMapper;

	private final Validator validator;

	public JsonAnswers(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
		this.validator = ModelOutputValidation.englishValidator();
	}

	/** Parses and validates one answer. Never throws: an invalid answer is a normal outcome. */
	public <T> Parsed<T> parse(String content, Class<T> type) {
		if (content == null || content.isBlank()) {
			return Parsed.invalid("the answer was empty");
		}
		T value;
		try {
			value = jsonMapper.readerFor(type)
				.with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
				.readValue(stripCodeFence(content));
		}
		catch (JacksonException ex) {
			// The parser's message can quote the model's text, so it is not passed on.
			return Parsed.invalid("the answer was not a JSON object with the expected fields and types");
		}
		if (value == null) {
			return Parsed.invalid("the answer was null");
		}
		Set<ConstraintViolation<T>> violations = validator.validate(value);
		if (!violations.isEmpty()) {
			return Parsed.invalid(violations.stream()
				.map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
				.sorted()
				.collect(Collectors.joining("; ")));
		}
		return Parsed.valid(value);
	}

	public String write(Object value) {
		return jsonMapper.writeValueAsString(value);
	}

	public <T> T read(String json, Class<T> type) {
		return json == null ? null : jsonMapper.readValue(json, type);
	}

	/** SHA-256 of the JSON form of a step's input: equal hashes mean an identical input. */
	public String hash(Object input) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(write(input).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is always available in the JDK", ex);
		}
	}

	public static String loadSchema(String path) {
		try {
			return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not read the schema " + path, ex);
		}
	}

	/** Some models wrap JSON in a Markdown code block even when told not to. */
	static String stripCodeFence(String content) {
		String text = content.strip();
		if (text.startsWith("```")) {
			int firstLineEnd = text.indexOf('\n');
			int closingFence = text.lastIndexOf("```");
			if (firstLineEnd > 0 && closingFence > firstLineEnd) {
				return text.substring(firstLineEnd + 1, closingFence).strip();
			}
		}
		return text;
	}

	/** Outcome of parsing one answer: a valid value, or a description of what was wrong. */
	public record Parsed<T>(T value, String problem) {

		static <T> Parsed<T> valid(T value) {
			return new Parsed<>(value, null);
		}

		static <T> Parsed<T> invalid(String problem) {
			return new Parsed<>(null, problem);
		}

		public boolean isValid() {
			return value != null;
		}

	}

}
