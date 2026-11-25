package com.example.exam.controller;

import com.example.exam.model.Exam;
import com.example.exam.model.ExamAnswer;
import com.example.exam.model.ExamResult;
import com.example.exam.model.Question;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamControllerAnswerWorkflowTests {

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

    private User student;
    private Exam exam;
    private ExamResult attempt;
    private Question correctQuestion;
    private Question incorrectQuestion;

    @BeforeEach
    void setUp() {
        student = new User();
        student.setId(3L);
        student.setUsername("student@example.com");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("student@example.com", "n/a", List.of()));
        when(userRepository.findByUsername("student@example.com")).thenReturn(Optional.of(student));

        exam = new Exam();
        exam.setId(7L);
        exam.setWebcamMonitoringEnabled(false);
        student.getAssignedExams().add(exam);

        correctQuestion = new Question();
        correctQuestion.setId(11L);
        correctQuestion.setExam(exam);
        correctQuestion.setCorrectAnswer(1);
        correctQuestion.setMarks(3);

        incorrectQuestion = new Question();
        incorrectQuestion.setId(12L);
        incorrectQuestion.setExam(exam);
        incorrectQuestion.setCorrectAnswer(2);
        incorrectQuestion.setMarks(2);
        exam.setQuestions(List.of(correctQuestion, incorrectQuestion));

        attempt = new ExamResult();
        attempt.setId(19L);
        attempt.setStudent(student);
        attempt.setExam(exam);
        attempt.setCompletionStatus("IN_PROGRESS");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void answerAutosavesWhenWebcamMonitoringIsDisabled() {
        stubActiveAttempt();
        stubExam();
        when(questionRepository.findById(11L)).thenReturn(Optional.of(correctQuestion));
        when(examAnswerRepository.findByExamResultAndQuestion(attempt, correctQuestion))
                .thenReturn(Optional.empty());

        ResponseEntity<Map<String, String>> response = postAnswer(11L, 1);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<ExamAnswer> savedAnswer = ArgumentCaptor.forClass(ExamAnswer.class);
        verify(examAnswerRepository).save(savedAnswer.capture());
        assertEquals(1, savedAnswer.getValue().getSelectedOption());
        assertEquals(attempt, savedAnswer.getValue().getExamResult());
    }

    @Test
    void answerAutosavesWhenWebcamMonitoringIsEnabled() {
        exam.setWebcamMonitoringEnabled(true);
        attempt.setWebcamMonitoringEnabled(true);
        stubActiveAttempt();
        stubExam();
        when(questionRepository.findById(11L)).thenReturn(Optional.of(correctQuestion));
        when(examAnswerRepository.findByExamResultAndQuestion(attempt, correctQuestion))
                .thenReturn(Optional.empty());

        ResponseEntity<Map<String, String>> response = postAnswer(11L, 2);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<ExamAnswer> savedAnswer = ArgumentCaptor.forClass(ExamAnswer.class);
        verify(examAnswerRepository).save(savedAnswer.capture());
        assertEquals(2, savedAnswer.getValue().getSelectedOption());
    }

    @Test
    void inactiveAttemptCannotAutosaveAnswers() {
        stubExam();
        when(questionRepository.findById(11L)).thenReturn(Optional.of(correctQuestion));
        when(examResultRepository.findFirstByExamAndStudentAndCompletionStatusOrderByStartedAtDesc(
                exam, student, "IN_PROGRESS")).thenReturn(Optional.empty());

        ResponseEntity<Map<String, String>> response = postAnswer(11L, 1);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(examResultRepository)
                .findFirstByExamAndStudentAndCompletionStatusOrderByStartedAtDesc(
                        exam, student, "IN_PROGRESS");
        verify(examAnswerRepository, never()).save(any(ExamAnswer.class));
    }

    @Test
    void unassignedStudentCannotOpenExamPageByUrl() {
        student.getAssignedExams().clear();
        when(examRepository.findById(7L)).thenReturn(Optional.of(exam));

        String destination = examController.getExamPage(
                7L, new ExtendedModelMap(), new RedirectAttributesModelMap());

        assertEquals("redirect:/student/dashboard", destination);
        verify(examResultRepository, never()).existsByExamAndStudent(any(), any());
    }

    @Test
    void unassignedStudentCannotStartExamThroughPostEndpoint() {
        student.getAssignedExams().clear();
        when(examRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(exam));

        ResponseEntity<Map<String, Object>> response = examController.startExam(7L);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(examResultRepository, never()).save(any(ExamResult.class));
    }

    @Test
    void questionFromAnotherExamCannotAutosave() {
        Exam otherExam = new Exam();
        otherExam.setId(8L);
        Question foreignQuestion = new Question();
        foreignQuestion.setId(99L);
        foreignQuestion.setExam(otherExam);
        when(examRepository.findById(7L)).thenReturn(Optional.of(exam));
        when(questionRepository.findById(99L)).thenReturn(Optional.of(foreignQuestion));

        ResponseEntity<Map<String, String>> response = postAnswer(99L, 1);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(examResultRepository, never())
                .findFirstByExamAndStudentAndCompletionStatusOrderByStartedAtDesc(any(), any(), any());
        verify(examAnswerRepository, never()).save(any(ExamAnswer.class));
    }

    @Test
    void normalSubmissionScoresCorrectAnswersAndAwardsNoMarksForIncorrectAnswers() {
        stubExam();
        stubActiveAttempt();
        when(examAnswerRepository.findByExamResult(attempt)).thenReturn(List.of());

        String destination = submit(Map.of("q_11", "1", "q_12", "1"), "");

        assertEquals("redirect:/student/result", destination);
        assertEquals(3, attempt.getScoreAchieved());
        assertEquals(5, attempt.getTotalMarks());
        assertEquals("COMPLETED", attempt.getCompletionStatus());
        assertNotNull(attempt.getSubmissionTime());
        assertTrue(attempt.getTerminationReason() == null);
        verify(examAnswerRepository, times(2)).save(any(ExamAnswer.class));
    }

    @Test
    void timeoutStyleSubmissionUsesNormalScoringAndCompletionWorkflow() {
        stubExam();
        stubActiveAttempt();
        when(examAnswerRepository.findByExamResult(attempt)).thenReturn(List.of());

        String destination = submit(Map.of("q_11", "1", "q_12", "2"), "");

        assertEquals("redirect:/student/result", destination);
        assertEquals(5, attempt.getScoreAchieved());
        assertEquals("COMPLETED", attempt.getCompletionStatus());
        assertNotNull(attempt.getSubmissionTime());
    }

    @Test
    void violationTerminatedAttemptScoresSavedAnswersAndRetainsProctoringMetadata() {
        exam.setWebcamMonitoringEnabled(true);
        attempt.setWebcamMonitoringEnabled(true);
        attempt.setCompletionStatus("TERMINATED");
        attempt.setTerminationReason("Maximum looking-away violations reached.");
        attempt.setTerminatedAt(LocalDateTime.now().minusMinutes(1));
        attempt.setLookingAwayViolationCount(8);
        attempt.setProhibitedDeviceViolationCount(1);
        attempt.setMobilePhoneDetectionCount(1);
        stubExam();
        when(examResultRepository.findFirstByExamAndStudentAndCompletionStatusOrderByStartedAtDesc(
                exam, student, "IN_PROGRESS")).thenReturn(Optional.empty());
        when(examResultRepository.findFirstByExamAndStudentAndCompletionStatusOrderByStartedAtDesc(
                exam, student, "TERMINATED")).thenReturn(Optional.of(attempt));
        when(examAnswerRepository.findByExamResult(attempt)).thenReturn(List.of(
                new ExamAnswer(attempt, correctQuestion, 1),
                new ExamAnswer(attempt, incorrectQuestion, 1)));

        String destination = submit(Map.of(), "");

        assertEquals("redirect:/student/result", destination);
        assertEquals(3, attempt.getScoreAchieved());
        assertEquals(5, attempt.getTotalMarks());
        assertEquals("TERMINATED", attempt.getCompletionStatus());
        assertEquals("Maximum looking-away violations reached.", attempt.getTerminationReason());
        assertEquals(8, attempt.getLookingAwayViolationCount());
        assertEquals(1, attempt.getProhibitedDeviceViolationCount());
        assertEquals(1, attempt.getMobilePhoneDetectionCount());
        assertNotNull(attempt.getTerminatedAt());
        assertNotNull(attempt.getSubmissionTime());
    }

    @Test
    void terminatedAttemptCannotRestartOrBeSubmittedTwice() {
        attempt.setCompletionStatus("TERMINATED");
        attempt.setTerminationReason("Second prohibited-device violation detected.");
        attempt.setProhibitedDeviceViolationCount(2);
        attempt.setSubmissionTime(LocalDateTime.now().minusSeconds(1));
        when(examRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(exam));
        when(examResultRepository.findFirstByExamAndStudentAndCompletionStatusOrderByStartedAtDesc(
                exam, student, "IN_PROGRESS")).thenReturn(Optional.empty());
        when(examResultRepository.existsByExamAndStudent(exam, student)).thenReturn(true);
        when(examRepository.findById(7L)).thenReturn(Optional.of(exam));
        when(examResultRepository.findFirstByExamAndStudentAndCompletionStatusOrderByStartedAtDesc(
                exam, student, "TERMINATED")).thenReturn(Optional.of(attempt));

        ResponseEntity<Map<String, Object>> startResponse = examController.startExam(7L);
        String submitDestination = submit(Map.of("q_11", "1"), "");

        assertEquals(HttpStatus.CONFLICT, startResponse.getStatusCode());
        assertEquals("redirect:/student/dashboard", submitDestination);
        assertEquals("TERMINATED", attempt.getCompletionStatus());
        assertEquals("Second prohibited-device violation detected.", attempt.getTerminationReason());
        assertEquals(2, attempt.getProhibitedDeviceViolationCount());
        verify(examAnswerRepository, never()).save(any(ExamAnswer.class));
        verify(examResultRepository, never()).save(any(ExamResult.class));
    }

    private ResponseEntity<Map<String, String>> postAnswer(long questionId, int option) {
        return examController.saveAnswer(Map.of(
                "examId", "7",
                "questionId", Long.toString(questionId),
                "selectedOption", Integer.toString(option)));
    }

    private String submit(Map<String, String> answers, String terminationReason) {
        return examController.submitExam(
                7L, answers, terminationReason, new RedirectAttributesModelMap());
    }

    private void stubExam() {
        when(examRepository.findById(7L)).thenReturn(Optional.of(exam));
    }

    private void stubActiveAttempt() {
        when(examResultRepository.findFirstByExamAndStudentAndCompletionStatusOrderByStartedAtDesc(
                exam, student, "IN_PROGRESS")).thenReturn(Optional.of(attempt));
    }

}
