package com.lecturerecorder.service;

import com.lecturerecorder.model.Doubt;
import com.lecturerecorder.model.Recording;
import com.lecturerecorder.model.User;
import com.lecturerecorder.repository.DoubtRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DoubtService {

    private final DoubtRepository doubtRepository;

    @Value("${app.doubt-threshold}")
    private int hotThreshold;

    // Points within this many seconds of each other are treated as "the same moment"
    private static final int BUCKET_SIZE_SECONDS = 5;

    public DoubtService(DoubtRepository doubtRepository) {
        this.doubtRepository = doubtRepository;
    }

    public void addDoubt(Recording recording, User student, int timestampSeconds, String comment) {
        Doubt doubt = new Doubt();
        doubt.setRecording(recording);
        doubt.setStudent(student);
        doubt.setTimestampSeconds(timestampSeconds);
        doubt.setComment(comment);
        doubtRepository.save(doubt);
    }

    private int bucketOf(int seconds) {
        return (seconds / BUCKET_SIZE_SECONDS) * BUCKET_SIZE_SECONDS;
    }

    /** Anonymous view for students: just where on the timeline doubts cluster, and how many - no names. */
    public List<AnonymousDoubtMarker> getAnonymousMarkers(Long recordingId) {
        List<Doubt> doubts = doubtRepository.findByRecordingIdOrderByTimestampSecondsAsc(recordingId);
        Map<Integer, Integer> counts = new TreeMap<>();
        for (Doubt d : doubts) {
            int bucket = bucketOf(d.getTimestampSeconds());
            counts.merge(bucket, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .map(e -> new AnonymousDoubtMarker(e.getKey(), e.getValue(), e.getValue() >= hotThreshold))
                .collect(Collectors.toList());
    }

    /** Full view for the teacher: real student names attached to each doubt. */
    public List<Doubt> getDoubtsForTeacher(Long recordingId) {
        return doubtRepository.findByRecordingIdOrderByTimestampSecondsAsc(recordingId);
    }

    public int getHotThreshold() {
        return hotThreshold;
    }

    public record AnonymousDoubtMarker(int bucketStartSeconds, int count, boolean hot) {}
}
