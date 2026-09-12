package com.lecturerecorder.repository;

import com.lecturerecorder.model.Quiz;
import com.lecturerecorder.model.QuizAttempt;
import com.lecturerecorder.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
    Optional<QuizAttempt> findByQuizAndStudent(Quiz quiz, User student);
    List<QuizAttempt> findByQuiz(Quiz quiz);
    List<QuizAttempt> findByStudent(User student);
}
