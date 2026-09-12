package com.lecturerecorder.repository;

import com.lecturerecorder.model.Lecture;
import com.lecturerecorder.model.Poll;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PollRepository extends JpaRepository<Poll, Long> {
    List<Poll> findByLectureOrderByCreatedAtAsc(Lecture lecture);
}
