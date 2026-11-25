package com.example.exam.controller;

import com.example.exam.model.User;
import com.example.exam.model.Exam;
import com.example.exam.model.ExamResult;
import com.example.exam.repository.ExamRepository;
import com.example.exam.repository.ExamResultRepository;
import com.example.exam.repository.QuestionRepository;
import com.example.exam.repository.UserRepository;
import com.example.exam.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class AdminControllerStudentManagementTests {

    @Mock
    private ExamRepository examRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private ExamResultRepository examResultRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;

    @InjectMocks
    private AdminController adminController;

    @Test
    void addStudentTrimsNameAndUsernameAndDelegatesPasswordEncodingToUserService() {
        when(userRepository.findByUsername("student01")).thenReturn(Optional.empty());
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String destination = adminController.addStudent(
                "  Alex Student  ", " student01 ", "secret", redirectAttributes);

        assertEquals("redirect:/admin/students", destination);
        ArgumentCaptor<User> studentCaptor = ArgumentCaptor.forClass(User.class);
        verify(userService).saveStudent(studentCaptor.capture());
        assertEquals("Alex Student", studentCaptor.getValue().getFullName());
        assertEquals("student01", studentCaptor.getValue().getUsername());
        assertEquals("secret", studentCaptor.getValue().getPassword());
    }

    @Test
    void duplicateUsernameIsRejectedWithoutSaving() {
        User existing = new User();
        when(userRepository.findByUsername("student01")).thenReturn(Optional.of(existing));
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String destination = adminController.addStudent(
                "Alex Student", "student01", "secret", redirectAttributes);

        assertEquals("redirect:/admin/students", destination);
        assertEquals("An account already exists with that username.",
                redirectAttributes.getFlashAttributes().get("errorMessage"));
        verify(userService, never()).saveStudent(org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    void assignExamsUpdatesExistingCollectionInPlace() {
        User student = new User();
        student.setId(4L);
        student.setUsername("student01");
        student.setRole("ROLE_STUDENT");
        Exam previouslyAssignedExam = new Exam();
        previouslyAssignedExam.setId(8L);
        student.getAssignedExams().add(previouslyAssignedExam);
        List<Exam> managedAssignments = student.getAssignedExams();
        Exam exam = new Exam();
        exam.setId(9L);
        when(userRepository.findById(4L)).thenReturn(Optional.of(student));
        when(examRepository.findAllById(List.of(9L))).thenReturn(List.of(exam));

        String destination = adminController.assignExams(
                4L, List.of(9L), new RedirectAttributesModelMap());

        assertEquals("redirect:/admin/students", destination);
        assertSame(managedAssignments, student.getAssignedExams());
        assertEquals(List.of(exam), student.getAssignedExams());
        verify(userRepository).save(student);
    }

    @Test
    void emptySelectionRemovesAllAssignmentsWithoutReplacingCollection() {
        User student = new User();
        student.setId(4L);
        student.setRole("ROLE_STUDENT");
        Exam previouslyAssignedExam = new Exam();
        previouslyAssignedExam.setId(8L);
        student.getAssignedExams().add(previouslyAssignedExam);
        List<Exam> managedAssignments = student.getAssignedExams();
        when(userRepository.findById(4L)).thenReturn(Optional.of(student));

        String destination = adminController.assignExams(
                4L, null, new RedirectAttributesModelMap());

        assertEquals("redirect:/admin/students", destination);
        assertSame(managedAssignments, student.getAssignedExams());
        assertEquals(List.of(), student.getAssignedExams());
        verify(userRepository).save(student);
    }

    @Test
    void deactivateStudentPreservesAccountAndExamResults() {
        User student = new User();
        student.setId(4L);
        student.setRole("ROLE_STUDENT");
        when(userRepository.findById(4L)).thenReturn(Optional.of(student));

        String destination = adminController.deactivateStudent(4L, new RedirectAttributesModelMap());

        assertEquals("redirect:/admin/students", destination);
        assertEquals(false, student.isActive());
        verify(userRepository).save(student);
        verify(examResultRepository, never()).deleteByStudent(any(User.class));
    }

    @Test
    void updateStudentChangesOnlyNameAndUsername() {
        User student = new User();
        student.setId(4L);
        student.setRole("ROLE_STUDENT");
        student.setUsername("old-name");
        when(userRepository.findById(4L)).thenReturn(Optional.of(student));
        when(userRepository.findByUsername("new-name")).thenReturn(Optional.empty());

        String destination = adminController.updateStudent(
                4L, "New Name", "new-name", new RedirectAttributesModelMap());

        assertEquals("redirect:/admin/students", destination);
        assertEquals("New Name", student.getFullName());
        assertEquals("new-name", student.getUsername());
        assertEquals("ROLE_STUDENT", student.getRole());
        assertEquals(true, student.isActive());
        verify(userRepository).save(student);
    }

    @Test
    void allResultsPagePassesBothFiltersToRepository() {
        User student = new User();
        student.setId(4L);
        student.setRole("ROLE_STUDENT");
        Exam exam = new Exam();
        exam.setId(9L);
        ExamResult result = new ExamResult();
        result.setStudent(student);
        result.setExam(exam);
        when(userRepository.findByRole("ROLE_STUDENT")).thenReturn(List.of(student));
        when(examRepository.findAll()).thenReturn(List.of(exam));
        when(examResultRepository.findAllFiltered(4L, 9L)).thenReturn(List.of(result));
        ExtendedModelMap model = new ExtendedModelMap();

        String view = adminController.viewAllResults(4L, 9L, model);

        assertEquals("admin/all_results", view);
        assertEquals(List.of(result), model.get("results"));
        assertEquals(4L, model.get("selectedStudentId"));
        assertEquals(9L, model.get("selectedExamId"));
        verify(examResultRepository).findAllFiltered(4L, 9L);
    }
}
