package com.example.exam.repository;

import com.example.exam.model.Exam;
import com.example.exam.model.ExamResult;
import com.example.exam.model.User;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@Import(ExamResultRepositoryFilterTests.PasswordEncoderTestConfiguration.class)
class ExamResultRepositoryFilterTests {

    @TestConfiguration
    static class PasswordEncoderTestConfiguration {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }

    @Autowired
    private ExamResultRepository examResultRepository;
    @Autowired
    private ExamRepository examRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TestEntityManager entityManager;

    @Test
    void filtersResultsByStudentAndExamIndependently() {
        User firstStudent = saveStudent("first");
        User secondStudent = saveStudent("second");
        Exam firstExam = saveExam("Java");
        Exam secondExam = saveExam("Spring");

        saveResult(firstStudent, firstExam);
        saveResult(firstStudent, secondExam);
        saveResult(secondStudent, firstExam);

        assertEquals(1, examResultRepository.findAllFiltered(firstStudent.getId(), firstExam.getId()).size());
        assertEquals(2, examResultRepository.findAllFiltered(firstStudent.getId(), null).size());
        assertEquals(2, examResultRepository.findAllFiltered(null, firstExam.getId()).size());
        assertEquals(3, examResultRepository.findAllFiltered(null, null).size());
    }

    @Test
    void studentExamAssignmentsPersistAndCanBeUpdated() {
        User student = saveStudent("assigned-student");
        Exam firstExam = saveExam("Java");
        Exam replacementExam = saveExam("Spring");

        student.getAssignedExams().add(firstExam);
        userRepository.saveAndFlush(student);
        entityManager.clear();

        User reloadedStudent = userRepository.findById(student.getId()).orElseThrow();
        assertEquals(List.of(firstExam.getId()),
                reloadedStudent.getAssignedExams().stream().map(Exam::getId).toList());

        reloadedStudent.getAssignedExams().clear();
        reloadedStudent.getAssignedExams().add(replacementExam);
        userRepository.saveAndFlush(reloadedStudent);
        entityManager.clear();

        User updatedStudent = userRepository.findById(student.getId()).orElseThrow();
        assertEquals(List.of(replacementExam.getId()),
                updatedStudent.getAssignedExams().stream().map(Exam::getId).toList());
    }

    private User saveStudent(String username) {
        User student = new User();
        student.setUsername(username);
        student.setPassword("encoded-password");
        student.setFullName(username);
        student.setRole("ROLE_STUDENT");
        return userRepository.saveAndFlush(student);
    }

    private Exam saveExam(String title) {
        Exam exam = new Exam();
        exam.setTitle(title);
        exam.setDurationInMinutes(30);
        return examRepository.saveAndFlush(exam);
    }

    private void saveResult(User student, Exam exam) {
        ExamResult result = new ExamResult();
        result.setStudent(student);
        result.setExam(exam);
        result.setScoreAchieved(8);
        result.setTotalMarks(10);
        result.setSubmissionTime(LocalDateTime.now());
        result.setCompletionStatus("COMPLETED");
        examResultRepository.saveAndFlush(result);
    }
}
