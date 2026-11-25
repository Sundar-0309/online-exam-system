package com.example.exam.controller;

import com.example.exam.model.Exam;
import com.example.exam.model.ExamResult;
import com.example.exam.model.User;
import com.example.exam.repository.ExamAnswerRepository;
import com.example.exam.repository.ExamRepository;
import com.example.exam.repository.ExamResultRepository;
import com.example.exam.repository.QuestionRepository;
import com.example.exam.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamControllerProctoringTests {

    @Mock
    private ExamRepository examRepository;
    @Mock
    private ExamResultRepository examResultRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private ExamAnswerRepository examAnswerRepository;

    @InjectMocks
    private ExamController examController;

    private Exam exam;
    private ExamResult attempt;

    @BeforeEach
    void setUp() {
        User student = new User();
        student.setUsername("student@example.com");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("student@example.com", "n/a", List.of()));
        when(userRepository.findByUsername("student@example.com")).thenReturn(Optional.of(student));

        exam = new Exam();
        exam.setId(7L);
        exam.setWebcamMonitoringEnabled(true);
        student.getAssignedExams().add(exam);
        when(examRepository.findById(7L)).thenReturn(Optional.of(exam));

        attempt = new ExamResult();
        attempt.setExam(exam);
        attempt.setWebcamMonitoringEnabled(true);
        attempt.setCompletionStatus("IN_PROGRESS");
        when(examResultRepository.findFirstByExamAndStudentAndCompletionStatusOrderByStartedAtDesc(
                exam, student, "IN_PROGRESS")).thenReturn(Optional.of(attempt));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void lookingAwayWarningsTerminateAtEightWithoutChangingDeviceCount() {
        for (int warning = 1; warning <= 8; warning++) {
            attempt.setLastLookingAwayViolationAt(warning == 1 ? null : LocalDateTime.now().minusSeconds(10));
            ResponseEntity<Map<String, Object>> response = examController.reportViolation(
                    Map.of("examId", "7", "category", "looking_away"));
            assertEquals(warning, response.getBody().get("warningCount"));
        }

        assertEquals(8, attempt.getLookingAwayViolationCount());
        assertEquals(0, attempt.getProhibitedDeviceViolationCount());
        assertEquals("TERMINATED", attempt.getCompletionStatus());
        assertTrue(attempt.getTerminationReason().contains("looking-away"));
    }

    @Test
    void phoneAndEarphoneDetectionsShareTheirOwnCounter() {
        attempt.setLastDeviceViolationAt(null);
        examController.reportViolation(Map.of(
                "examId", "7", "category", "prohibited_device", "objectType", "mobile_phone"));
        attempt.setLastDeviceViolationAt(LocalDateTime.now().minusSeconds(10));
        ResponseEntity<Map<String, Object>> response = examController.reportViolation(Map.of(
                "examId", "7", "category", "prohibited_device", "objectType", "earphones"));

        assertEquals(2, attempt.getProhibitedDeviceViolationCount());
        assertEquals(1, attempt.getMobilePhoneDetectionCount());
        assertEquals(1, attempt.getEarphoneDetectionCount());
        assertEquals(0, attempt.getLookingAwayViolationCount());
        assertEquals("TERMINATED", attempt.getCompletionStatus());
        assertEquals(true, response.getBody().get("terminated"));
        assertTrue(attempt.getDetectedObjectTypes().contains("Mobile phone"));
        assertTrue(attempt.getDetectedObjectTypes().contains("Earphones/earbuds"));
    }
}
