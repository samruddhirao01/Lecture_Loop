package com.lecturerecorder.repository;

import com.lecturerecorder.model.QuizResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuizResponseRepository extends JpaRepository<QuizResponse, Long> {
    List<QuizResponse> findByQuestionId(Long questionId);
    Optional<QuizResponse> findByQuestionIdAndStudentId(Long questionId, Long studentId);
    List<QuizResponse> findByQuestionIdInAndStudentId(List<Long> questionIds, Long studentId);
}
