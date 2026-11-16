package com.applypilot.user;

import com.applypilot.user.dto.UserResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public UserResponse getById(UUID id) {
		return userRepository.findById(id).map(UserResponse::from).orElseThrow(UserNotFoundException::new);
	}

}
