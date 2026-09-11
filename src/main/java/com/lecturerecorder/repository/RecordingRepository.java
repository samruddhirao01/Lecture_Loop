package com.lecturerecorder.repository;

import com.lecturerecorder.model.Recording;
import com.lecturerecorder.model.Section;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecordingRepository extends JpaRepository<Recording, Long> {
    List<Recording> findBySectionOrderByUploadedAtDesc(Section section);
    List<Recording> findByUploadedByIdOrderByUploadedAtDesc(Long teacherId);
}
