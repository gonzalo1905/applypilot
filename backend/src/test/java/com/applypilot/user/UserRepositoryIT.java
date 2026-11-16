package com.applypilot.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.applypilot.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class UserRepositoryIT {

	@Autowired
	private UserRepository userRepository;

	@Test
	void savesUserAndFindsItByEmail() {
		User saved = userRepository.saveAndFlush(new User("ada@example.com", "hash", "Ada Lovelace"));

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(userRepository.findByEmail("ada@example.com")).contains(saved);
		assertThat(userRepository.existsByEmail("nobody@example.com")).isFalse();
	}

	@Test
	void rejectsDuplicateEmail() {
		userRepository.saveAndFlush(new User("ada@example.com", "hash", "Ada Lovelace"));

		assertThatThrownBy(() -> userRepository.saveAndFlush(new User("ada@example.com", "other", "Another Ada")))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
