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
@RequestMapping("/api/bookmarks")
public class BookmarkController {

    private final UserRepository userRepository;
    private final LectureRepository lectureRepository;
    private final BookmarkRepository bookmarkRepository;

    public BookmarkController(UserRepository userRepository, LectureRepository lectureRepository,
                               BookmarkRepository bookmarkRepository) {
        this.userRepository = userRepository;
        this.lectureRepository = lectureRepository;
        this.bookmarkRepository = bookmarkRepository;
    }

    private User currentStudent(HttpSession session) {
        if (!"STUDENT".equals(session.getAttribute("role"))) return null;
        Long userId = (Long) session.getAttribute("userId");
        return userRepository.findById(userId).orElse(null);
    }

    @GetMapping
    public ResponseEntity<?> listForLecture(@RequestParam Long lectureId, HttpSession session) {
        User student = currentStudent(session);
        if (student == null) return ResponseEntity.status(403).body(Map.of("error", "Students only"));

        Optional<Lecture> lectureOpt = lectureRepository.findById(lectureId);
        if (lectureOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "Lecture not found"));
        if (!lectureOpt.get().getSection().getId().equals(student.getSection().getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not your section's lecture"));
        }

        List<Map<String, Object>> result = bookmarkRepository
                .findByStudentAndLectureOrderByTimestampSecondsAsc(student, lectureOpt.get())
                .stream().map(this::toMap).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<?> add(@RequestBody Map<String, Object> body, HttpSession session) {
        User student = currentStudent(session);
        if (student == null) return ResponseEntity.status(403).body(Map.of("error", "Students only"));

        Long lectureId = Long.valueOf(body.get("lectureId").toString());
        Optional<Lecture> lectureOpt = lectureRepository.findById(lectureId);
        if (lectureOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "Lecture not found"));
        if (!lectureOpt.get().getSection().getId().equals(student.getSection().getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not your section's lecture"));
        }

        Bookmark bookmark = new Bookmark();
        bookmark.setLecture(lectureOpt.get());
        bookmark.setStudent(student);
        bookmark.setTimestampSeconds(Integer.valueOf(body.get("timestampSeconds").toString()));
        bookmark.setNote(body.get("note") != null ? body.get("note").toString() : null);
        bookmark.setCreatedAt(LocalDateTime.now());
        bookmarkRepository.save(bookmark);

        return ResponseEntity.ok(toMap(bookmark));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpSession session) {
        User student = currentStudent(session);
        if (student == null) return ResponseEntity.status(403).body(Map.of("error", "Students only"));

        Optional<Bookmark> bookmarkOpt = bookmarkRepository.findById(id);
        if (bookmarkOpt.isEmpty() || !bookmarkOpt.get().getStudent().getId().equals(student.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not your bookmark"));
        }
        bookmarkRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }

    private Map<String, Object> toMap(Bookmark b) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", b.getId());
        m.put("timestampSeconds", b.getTimestampSeconds());
        m.put("note", b.getNote());
        m.put("createdAt", b.getCreatedAt().toString());
        return m;
    }
}
