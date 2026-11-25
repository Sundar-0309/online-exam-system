package com.example.exam.repository;

import com.example.exam.model.Exam;
import com.example.exam.model.ExamResult;
import com.example.exam.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ExamResultRepository extends JpaRepository<ExamResult, Long> {

    List<ExamResult> findByExam(Exam exam);
    @Query("select result from ExamResult result "
            + "where (:studentId is null or result.student.id = :studentId) "
            + "and (:examId is null or result.exam.id = :examId) "
            + "order by result.submissionTime desc")
    List<ExamResult> findAllFiltered(@Param("studentId") Long studentId, @Param("examId") Long examId);
    long countByCompletionStatusNot(String completionStatus);
    List<ExamResult> findTop5ByCompletionStatusNotOrderBySubmissionTimeDesc(String completionStatus);
    boolean existsByExamAndStudent(Exam exam, User student);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<ExamResult> findFirstByExamAndStudentAndCompletionStatusOrderByStartedAtDesc(
            Exam exam, User student, String completionStatus);
    @Transactional
    void deleteByStudent_Id(Long studentId);

    List<ExamResult> findByStudent(User student);
    List<ExamResult> findByStudentOrderBySubmissionTimeDesc(User student);
    List<ExamResult> findTop5ByOrderBySubmissionTimeDesc();

    @Transactional
    void deleteByStudent(User student);
}

