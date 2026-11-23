package com.applypilot.common.ai;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Locale;
import org.hibernate.validator.HibernateValidator;

/**
 * The validator for model answers. Its messages are sent back to the model in the retry
 * prompt, so they are always in English, whatever the server's locale (on a machine set to
 * Spanish the default validator would answer "no debe estar vacío").
 */
public final class ModelOutputValidation {

	private static final Validator ENGLISH = Validation.byProvider(HibernateValidator.class)
		.configure()
		.defaultLocale(Locale.ENGLISH)
		.buildValidatorFactory()
		.getValidator();

	private ModelOutputValidation() {
	}

	public static Validator englishValidator() {
		return ENGLISH;
	}

}
