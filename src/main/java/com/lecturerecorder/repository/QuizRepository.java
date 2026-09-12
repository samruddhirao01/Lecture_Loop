package com.lecturerecorder.repository;

import com.lecturerecorder.model.Lecture;
import com.lecturerecorder.model.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizRepository extends JpaRepository<Quiz, Long> {
    List<Quiz> findByLectureOrderByCreatedAtAsc(Lecture lecture);
}
