package com.lecturerecorder.repository;

import com.lecturerecorder.model.Doubt;
import com.lecturerecorder.model.Lecture;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DoubtRepository extends JpaRepository<Doubt, Long> {
    List<Doubt> findByLectureOrderByTimestampSecondsAsc(Lecture lecture);
    List<Doubt> findByStudent(com.lecturerecorder.model.User student);
}
