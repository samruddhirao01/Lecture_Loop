package com.lecturerecorder.repository;

import com.lecturerecorder.model.Lecture;
import com.lecturerecorder.model.Section;
import com.lecturerecorder.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LectureRepository extends JpaRepository<Lecture, Long> {
    List<Lecture> findBySectionOrderByUploadedAtDesc(Section section);
    List<Lecture> findByTeacherOrderByUploadedAtDesc(User teacher);
}
