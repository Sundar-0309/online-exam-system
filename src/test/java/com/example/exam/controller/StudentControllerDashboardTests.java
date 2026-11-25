package com.example.exam.controller;

import com.example.exam.model.Exam;
import com.example.exam.model.User;
import com.example.exam.repository.ExamResultRepository;
import com.example.exam.repository.UserRepository;
import com.example.exam.service.FileStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentControllerDashboardTests {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ExamResultRepository examResultRepository;
    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private StudentController studentController;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void dashboardListsOnlyAssignedUntakenExams() {
        User student = new User();
        student.setUsername("student01");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("student01", "n/a", List.of()));
        when(userRepository.findByUsername("student01")).thenReturn(Optional.of(student));
        when(examResultRepository.findByStudentOrderBySubmissionTimeDesc(student)).thenReturn(List.of());

        Exam assignedExam = new Exam();
        assignedExam.setId(1L);
        student.getAssignedExams().add(assignedExam);

        ExtendedModelMap model = new ExtendedModelMap();
        String view = studentController.studentDashboard(model);

        assertEquals("student/dashboard", view);
        assertEquals(List.of(assignedExam), model.get("availableExams"));
    }
}
