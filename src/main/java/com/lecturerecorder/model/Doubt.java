package com.lecturerecorder.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "doubts")
public class Doubt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "recording_id", nullable = false)
    private Recording recording;

    // The student who raised it. Only ever shown to TEACHER/ADMIN views -
    // student-facing templates must never render this field.
    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(nullable = false)
    private int timestampSeconds; // position in the lecture video/audio

    private String comment; // optional note from the student, still anonymous to peers

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Recording getRecording() { return recording; }
    public void setRecording(Recording recording) { this.recording = recording; }

    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }

    public int getTimestampSeconds() { return timestampSeconds; }
    public void setTimestampSeconds(int timestampSeconds) { this.timestampSeconds = timestampSeconds; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
