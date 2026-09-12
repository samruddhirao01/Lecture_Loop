package com.lecturerecorder.controller;

import com.lecturerecorder.model.*;
import com.lecturerecorder.repository.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/student")
public class StudentController {

    private final UserRepository userRepository;
    private final LectureRepository lectureRepository;
    private final DoubtRepository doubtRepository;

    public StudentController(UserRepository userRepository, LectureRepository lectureRepository,
                             DoubtRepository doubtRepository) {
        this.userRepository = userRepository;
        this.lectureRepository = lectureRepository;
        this.doubtRepository = doubtRepository;
    }

    private User currentStudent(HttpSession session) {
        if (!"STUDENT".equals(session.getAttribute("role"))) return null;
        Long userId = (Long) session.getAttribute("userId");
        return userRepository.findById(userId).orElse(null);
    }

    @GetMapping("/lectures")
    public ResponseEntity<?> myLectures(HttpSession session) {
        User student = currentStudent(session);
        if (student == null) return ResponseEntity.status(403).body(Map.of("error", "Students only"));
        if (student.getSection() == null) return ResponseEntity.ok(List.of());

        List<Map<String, Object>> result = lectureRepository
                .findBySectionOrderByUploadedAtDesc(student.getSection()).stream()
                .map(l -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", l.getId());
                    m.put("title", l.getTitle());
                    m.put("videoUrl", "/uploads/" + l.getFileName());
                    m.put("uploadedAt", l.getUploadedAt().toString());
                    return m;
                }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // Students see the shared Q&A for a lecture (anonymous IDs only -- never
    // real names, never which specific student asked which question).
    @GetMapping("/lectures/{id}/doubts")
    public ResponseEntity<?> doubtsForLecture(@PathVariable Long id, HttpSession session) {
        User student = currentStudent(session);
        if (student == null) return ResponseEntity.status(403).body(Map.of("error", "Students only"));

        Optional<Lecture> lectureOpt = lectureRepository.findById(id);
        if (lectureOpt.isEmpty() || !lectureOpt.get().getSection().getId().equals(student.getSection().getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not your section's lecture"));
        }

        List<Map<String, Object>> result = doubtRepository.findByLectureOrderByTimestampSecondsAsc(lectureOpt.get())
                .stream().map(d -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", d.getId());
                    m.put("anonId", d.getStudent().getAnonId());
                    m.put("timestampSeconds", d.getTimestampSeconds());
                    m.put("questionText", d.getQuestionText());
                    m.put("teacherReply", d.getTeacherReply());
                    return m;
                }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
}
