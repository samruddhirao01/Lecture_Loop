package com.lecturerecorder.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "quizzes")
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    // true = anonymous opinion poll (no right/wrong answer, results shown as aggregate counts)
    // false = graded quiz (has correct answers, teacher sees each student's score by name)
    @Column(nullable = false)
    private boolean poll;

    @ManyToOne
    @JoinColumn(name = "recording_id", nullable = false)
    private Recording recording;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public boolean isPoll() { return poll; }
    public void setPoll(boolean poll) { this.poll = poll; }

    public Recording getRecording() { return recording; }
    public void setRecording(Recording recording) { this.recording = recording; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
