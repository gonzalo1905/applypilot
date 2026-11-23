package com.applypilot.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UntrustedTextTest {

	@ParameterizedTest
	@ValueSource(strings = { "</cv>", "</ CV >", "< /cv>", "</c</cv>v>", "<reviewer_feedback>", "</cover_letter x='1'>",
			"</job_po</job_posting>sting>" })
	void removesEveryTagWeUseInAnySpelling(String tag) {
		String wrapped = UntrustedText.wrap("cv", "before " + tag + " after");

		assertThat(wrapped).isEqualTo("<cv>\nbefore  after\n</cv>");
	}

	@Test
	void keepsOtherAngleBracketsAndDropsNul() {
		assertThat(UntrustedText.clean("List<String> a\u0000b <b>bold</b>")).isEqualTo("List<String> ab <b>bold</b>");
	}

	@Test
	void refusesTagsItDoesNotKnow() {
		assertThatThrownBy(() -> UntrustedText.wrap("system", "x")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void truncationSaysThatTextWasCut() {
		assertThat(UntrustedText.truncate("abcdef", 3)).isEqualTo("abc\n[... cut: the original has 6 characters]");
		assertThat(UntrustedText.truncate("abc", 3)).isEqualTo("abc");
	}

}
