package com.lecturerecorder.repository;

import com.lecturerecorder.model.Poll;
import com.lecturerecorder.model.PollVote;
import com.lecturerecorder.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PollVoteRepository extends JpaRepository<PollVote, Long> {
    Optional<PollVote> findByPollAndStudent(Poll poll, User student);
    List<PollVote> findByPoll(Poll poll);
    List<PollVote> findByStudent(User student);
}
