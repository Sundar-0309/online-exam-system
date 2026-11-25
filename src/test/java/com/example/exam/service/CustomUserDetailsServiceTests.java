package com.example.exam.service;

import com.example.exam.model.User;
import com.example.exam.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTests {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    @Test
    void deactivatedStudentCannotAuthenticate() {
        User student = new User();
        student.setUsername("student01");
        student.setPassword("encoded-password");
        student.setRole("ROLE_STUDENT");
        student.setActive(false);
        when(userRepository.findByUsername("student01")).thenReturn(Optional.of(student));

        UserDetails userDetails = userDetailsService.loadUserByUsername("student01");

        assertFalse(userDetails.isEnabled());
    }
}
