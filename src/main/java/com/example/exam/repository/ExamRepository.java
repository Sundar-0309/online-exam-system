package com.example.exam.repository;

import com.example.exam.model.Exam;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExamRepository extends JpaRepository<Exam, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select exam from Exam exam where exam.id = :examId")
    java.util.Optional<Exam> findByIdForUpdate(@Param("examId") Long examId);
}
/***
 Subscribe Lazycoder - https://www.youtube.com/c/LazyCoderOnline?sub_confirmation=1
 whatsapp - https://wa.me/919572181024
 email - wapka1503@gmail.com
 ***/