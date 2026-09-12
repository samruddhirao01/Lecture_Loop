package com.lecturerecorder.controller;

import com.lecturerecorder.model.*;
import com.lecturerecorder.repository.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/polls")
public class PollController {

    private final UserRepository userRepository;
    private final LectureRepository lectureRepository;
    private final PollRepository pollRepository;
    private final PollOptionRepository pollOptionRepository;
    private final PollVoteRepository pollVoteRepository;

    public PollController(UserRepository userRepository, LectureRepository lectureRepository,
                           PollRepository pollRepository, PollOptionRepository pollOptionRepository,
                           PollVoteRepository pollVoteRepository) {
        this.userRepository = userRepository;
        this.lectureRepository = lectureRepository;
        this.pollRepository = pollRepository;
        this.pollOptionRepository = pollOptionRepository;
        this.pollVoteRepository = pollVoteRepository;
    }

    private User currentUser(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return null;
        return userRepository.findById(userId).orElse(null);
    }

    // Teacher creates a poll: { lectureId, question, options: ["text1", "text2", ...] }
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body, HttpSession session) {
        if (!"TEACHER".equals(session.getAttribute("role"))) {
            return ResponseEntity.status(403).body(Map.of("error", "Teachers only"));
        }
        User teacher = currentUser(session);

        Long lectureId = Long.valueOf(body.get("lectureId").toString());
        Optional<Lecture> lectureOpt = lectureRepository.findById(lectureId);
        if (lectureOpt.isEmpty() || !lectureOpt.get().getTeacher().getId().equals(teacher.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not your lecture"));
        }

        @SuppressWarnings("unchecked")
        List<String> optionTexts = (List<String>) body.get("options");
        if (optionTexts == null || optionTexts.size() < 2 || optionTexts.size() > 4) {
            return ResponseEntity.badRequest().body(Map.of("error", "Provide between 2 and 4 options"));
        }

        Poll poll = new Poll();
        poll.setLecture(lectureOpt.get());
        poll.setQuestion(body.get("question").toString());
        poll.setCreatedAt(LocalDateTime.now());
        pollRepository.save(poll);

        for (String text : optionTexts) {
            PollOption option = new PollOption();
            option.setPoll(poll);
            option.setOptionText(text);
            pollOptionRepository.save(option);
        }

        return ResponseEntity.ok(Map.of("id", poll.getId(), "message", "Poll created"));
    }

    // List polls for a lecture, with anonymous option-wise vote counts.
    @GetMapping
    public ResponseEntity<?> listForLecture(@RequestParam Long lectureId, HttpSession session) {
        User user = currentUser(session);
        if (user == null) return ResponseEntity.status(403).body(Map.of("error", "Not logged in"));

        Optional<Lecture> lectureOpt = lectureRepository.findById(lectureId);
        if (lectureOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "Lecture not found"));

        boolean isStudent = "STUDENT".equals(session.getAttribute("role"));
        if (isStudent && (user.getSection() == null
                || !lectureOpt.get().getSection().getId().equals(user.getSection().getId()))) {
            return ResponseEntity.status(403).body(Map.of("error", "Not your section's lecture"));
        }

        List<Map<String, Object>> result = pollRepository.findByLectureOrderByCreatedAtAsc(lectureOpt.get())
                .stream().map(p -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", p.getId());
                    m.put("question", p.getQuestion());

                    List<PollVote> votes = pollVoteRepository.findByPoll(p);
                    Optional<PollVote> myVote = "STUDENT".equals(session.getAttribute("role"))
                            ? pollVoteRepository.findByPollAndStudent(p, user) : Optional.empty();

                    List<Map<String, Object>> options = p.getOptions().stream().map(o -> {
                        Map<String, Object> om = new HashMap<>();
                        om.put("id", o.getId());
                        om.put("text", o.getOptionText());
                        // Vote counts are option-wise only -- never tied to a student identity.
                        long count = votes.stream().filter(v -> v.getOption().getId().equals(o.getId())).count();
                        om.put("voteCount", count);
                        return om;
                    }).collect(Collectors.toList());

                    m.put("options", options);
                    m.put("totalVotes", votes.size());
                    m.put("alreadyVoted", myVote.isPresent());
                    myVote.ifPresent(v -> m.put("yourOptionId", v.getOption().getId()));
                    return m;
                }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // Student votes
    @PostMapping("/{id}/vote")
    public ResponseEntity<?> vote(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpSession session) {
        if (!"STUDENT".equals(session.getAttribute("role"))) {
            return ResponseEntity.status(403).body(Map.of("error", "Students only"));
        }
        User student = currentUser(session);

        Optional<Poll> pollOpt = pollRepository.findById(id);
        if (pollOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "Poll not found"));
        Poll poll = pollOpt.get();

        if (student.getSection() == null
                || !poll.getLecture().getSection().getId().equals(student.getSection().getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not your section's poll"));
        }

        if (pollVoteRepository.findByPollAndStudent(poll, student).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "You already voted on this poll"));
        }

        Long optionId = Long.valueOf(body.get("optionId").toString());
        PollOption selected = poll.getOptions().stream()
                .filter(o -> o.getId().equals(optionId)).findFirst().orElse(null);
        if (selected == null) return ResponseEntity.badRequest().body(Map.of("error", "Invalid option"));

        PollVote vote = new PollVote();
        vote.setPoll(poll);
        vote.setStudent(student);
        vote.setOption(selected);
        vote.setVotedAt(LocalDateTime.now());
        pollVoteRepository.save(vote);

        return ResponseEntity.ok(Map.of("message", "Vote recorded"));
    }
}
