package com.lecturerecorder.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "quiz_attempts", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"quiz_id", "student_id"}) // one attempt per student per quiz
})
public class QuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne
    @JoinColumn(name = "selected_option_id", nullable = false)
    private QuizOption selectedOption;

    @Column(nullable = false)
    private boolean correct;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Quiz getQuiz() { return quiz; }
    public void setQuiz(Quiz quiz) { this.quiz = quiz; }

    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }

    public QuizOption getSelectedOption() { return selectedOption; }
    public void setSelectedOption(QuizOption selectedOption) { this.selectedOption = selectedOption; }

    public boolean isCorrect() { return correct; }
    public void setCorrect(boolean correct) { this.correct = correct; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
}
