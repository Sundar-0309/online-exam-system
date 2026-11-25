package com.example.exam.repository;

import com.example.exam.model.ExamAnswer;
import com.example.exam.model.ExamResult;
import com.example.exam.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExamAnswerRepository extends JpaRepository<ExamAnswer, Long> {
    List<ExamAnswer> findByExamResult(ExamResult examResult);
    Optional<ExamAnswer> findByExamResultAndQuestion(ExamResult examResult, Question question);
}
/***
 Subscribe Lazycoder - https://www.youtube.com/c/LazyCoderOnline?sub_confirmation=1
 whatsapp - https://wa.me/919572181024
 email - wapka1503@gmail.com
 ***/