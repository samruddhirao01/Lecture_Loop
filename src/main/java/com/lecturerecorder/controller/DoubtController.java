package com.lecturerecorder.controller;

import com.lecturerecorder.model.*;
import com.lecturerecorder.repository.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/doubts")
public class DoubtController {

    private final UserRepository userRepository;
    private final LectureRepository lectureRepository;
    private final DoubtRepository doubtRepository;

    public DoubtController(UserRepository userRepository, LectureRepository lectureRepository,
                           DoubtRepository doubtRepository) {
        this.userRepository = userRepository;
        this.lectureRepository = lectureRepository;
        this.doubtRepository = doubtRepository;
    }

    // Student posts a new anonymous doubt at a given timestamp in a lecture
    @PostMapping
    public ResponseEntity<?> postDoubt(@RequestBody Map<String, Object> body, HttpSession session) {
        if (!"STUDENT".equals(session.getAttribute("role"))) {
            return ResponseEntity.status(403).body(Map.of("error", "Students only"));
        }
        Long userId = (Long) session.getAttribute("userId");
        Optional<User> studentOpt = userRepository.findById(userId);
        if (studentOpt.isEmpty()) return ResponseEntity.status(403).body(Map.of("error", "Students only"));
        User student = studentOpt.get();

        Long lectureId = Long.valueOf(body.get("lectureId").toString());
        Optional<Lecture> lectureOpt = lectureRepository.findById(lectureId);
        if (lectureOpt.isEmpty() || !lectureOpt.get().getSection().getId().equals(student.getSection().getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not your section's lecture"));
        }

        Doubt doubt = new Doubt();
        doubt.setLecture(lectureOpt.get());
        doubt.setStudent(student);
        doubt.setTimestampSeconds(Integer.valueOf(body.get("timestampSeconds").toString()));
        doubt.setQuestionText(body.get("questionText").toString());
        doubt.setCreatedAt(LocalDateTime.now());
        doubtRepository.save(doubt);

        return ResponseEntity.ok(Map.of(
                "id", doubt.getId(),
                "anonId", student.getAnonId(),
                "message", "Doubt posted anonymously"
        ));
    }

    // Teacher replies to a doubt
    @PostMapping("/{id}/reply")
    public ResponseEntity<?> reply(@PathVariable Long id, @RequestBody Map<String, String> body, HttpSession session) {
        if (!"TEACHER".equals(session.getAttribute("role"))) {
            return ResponseEntity.status(403).body(Map.of("error", "Teachers only"));
        }
        Long teacherId = (Long) session.getAttribute("userId");

        Optional<Doubt> doubtOpt = doubtRepository.findById(id);
        if (doubtOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "Doubt not found"));

        Doubt doubt = doubtOpt.get();
        if (!doubt.getLecture().getTeacher().getId().equals(teacherId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Not your lecture"));
        }

        doubt.setTeacherReply(body.get("reply"));
        doubtRepository.save(doubt);
        return ResponseEntity.ok(Map.of("message", "Reply saved"));
    }
}
