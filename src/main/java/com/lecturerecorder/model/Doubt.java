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
    @JoinColumn(name = "lecture_id", nullable = false)
    private Lecture lecture;

    // Real student reference -- only ever exposed to ADMIN/TEACHER via lookups,
    // never sent to other students in API responses.
    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    // Timestamp in the lecture video (seconds) where the doubt was raised
    @Column(nullable = false)
    private Integer timestampSeconds;

    @Column(nullable = false, length = 1000)
    private String questionText;

    @Column(length = 2000)
    private String teacherReply;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Doubt() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Lecture getLecture() { return lecture; }
    public void setLecture(Lecture lecture) { this.lecture = lecture; }

    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }

    public Integer getTimestampSeconds() { return timestampSeconds; }
    public void setTimestampSeconds(Integer timestampSeconds) { this.timestampSeconds = timestampSeconds; }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public String getTeacherReply() { return teacherReply; }
    public void setTeacherReply(String teacherReply) { this.teacherReply = teacherReply; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
