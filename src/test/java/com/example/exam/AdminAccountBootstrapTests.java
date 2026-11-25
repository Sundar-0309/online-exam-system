package com.example.exam;

import com.example.exam.model.User;
import com.example.exam.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminAccountBootstrapTests {

	@Test
	void productionBootstrapRequiresEnvironmentVariables() throws NoSuchMethodException {
		Method bootstrapMethod = OnlineExamApplication.class.getDeclaredMethod(
				"createProductionAdminUser",
				UserRepository.class, PasswordEncoder.class, String.class, String.class);

		assertArrayEquals(new String[]{"prod"}, bootstrapMethod.getAnnotation(Profile.class).value());
		assertEquals("${ADMIN_USERNAME}",
				bootstrapMethod.getParameters()[2].getAnnotation(Value.class).value());
		assertEquals("${ADMIN_PASSWORD}",
				bootstrapMethod.getParameters()[3].getAnnotation(Value.class).value());
	}

	@Test
	void productionBootstrapCreatesAdminWithEncodedEnvironmentPassword() throws Exception {
		UserRepository userRepository = mock(UserRepository.class);
		PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
		when(userRepository.findByUsername("configured-admin")).thenReturn(Optional.empty());
		when(passwordEncoder.encode("test-only-value")).thenReturn("encoded-value");

		CommandLineRunner runner = new OnlineExamApplication().createProductionAdminUser(
				userRepository, passwordEncoder, "configured-admin", "test-only-value");
		runner.run();

		ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(savedUser.capture());
		assertEquals("configured-admin", savedUser.getValue().getUsername());
		assertEquals("encoded-value", savedUser.getValue().getPassword());
		assertEquals("ROLE_ADMIN", savedUser.getValue().getRole());
		verify(passwordEncoder).encode("test-only-value");
	}

	@Test
	void productionBootstrapLeavesAnExistingAdminUnchanged() throws Exception {
		UserRepository userRepository = mock(UserRepository.class);
		PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
		when(userRepository.findByUsername("configured-admin")).thenReturn(Optional.of(new User()));

		CommandLineRunner runner = new OnlineExamApplication().createProductionAdminUser(
				userRepository, passwordEncoder, "configured-admin", "test-only-value");
		runner.run();

		verify(userRepository, never()).save(any(User.class));
		verifyNoInteractions(passwordEncoder);
	}
}
