package com.lecturerecorder.repository;

import com.lecturerecorder.model.QuizOption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizOptionRepository extends JpaRepository<QuizOption, Long> {
}
