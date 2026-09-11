package com.lecturerecorder.repository;

import com.lecturerecorder.model.Doubt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DoubtRepository extends JpaRepository<Doubt, Long> {
    List<Doubt> findByRecordingIdOrderByTimestampSecondsAsc(Long recordingId);
    List<Doubt> findByRecordingIdAndStudentIdOrderByTimestampSecondsAsc(Long recordingId, Long studentId);
}
