package com.example.exam.config;

import com.example.exam.model.User;
import com.example.exam.repository.UserRepository;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActiveStudentFilterTests {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ActiveStudentFilter activeStudentFilter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deactivatedStudentSessionIsInvalidatedBeforeStudentRoute() throws ServletException, IOException {
        User student = new User();
        student.setUsername("student01");
        student.setActive(false);
        when(userRepository.findByUsername("student01")).thenReturn(Optional.of(student));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("student01", "password",
                        List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/student/dashboard");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        activeStudentFilter.doFilter(request, response, filterChain);

        assertEquals("/login?disabled=true", response.getRedirectedUrl());
        assertNull(filterChain.getRequest());
    }
}
