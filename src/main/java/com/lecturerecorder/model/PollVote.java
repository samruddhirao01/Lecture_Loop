package com.lecturerecorder.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "poll_votes", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"poll_id", "student_id"}) // one vote per student per poll
})
public class PollVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "poll_id", nullable = false)
    private Poll poll;

    // Stored only to enforce "one vote per student" -- never exposed in any
    // API response, so votes stay anonymous to teachers and other students.
    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne
    @JoinColumn(name = "option_id", nullable = false)
    private PollOption option;

    @Column(nullable = false)
    private LocalDateTime votedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Poll getPoll() { return poll; }
    public void setPoll(Poll poll) { this.poll = poll; }

    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }

    public PollOption getOption() { return option; }
    public void setOption(PollOption option) { this.option = option; }

    public LocalDateTime getVotedAt() { return votedAt; }
    public void setVotedAt(LocalDateTime votedAt) { this.votedAt = votedAt; }
}
