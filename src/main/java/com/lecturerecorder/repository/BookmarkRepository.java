package com.lecturerecorder.repository;

import com.lecturerecorder.model.Bookmark;
import com.lecturerecorder.model.Lecture;
import com.lecturerecorder.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {
    List<Bookmark> findByStudentAndLectureOrderByTimestampSecondsAsc(User student, Lecture lecture);
    List<Bookmark> findByStudent(User student);
    List<Bookmark> findByLecture(Lecture lecture);
}
