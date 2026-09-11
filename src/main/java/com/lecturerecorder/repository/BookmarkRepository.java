package com.lecturerecorder.repository;

import com.lecturerecorder.model.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {
    List<Bookmark> findByRecordingIdAndStudentIdOrderByTimestampSecondsAsc(Long recordingId, Long studentId);
    List<Bookmark> findByRecordingId(Long recordingId);
}
