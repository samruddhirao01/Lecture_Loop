package com.lecturerecorder.controller;

import com.lecturerecorder.model.*;
import com.lecturerecorder.repository.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    private final UserRepository userRepository;
    private final SectionRepository sectionRepository;
    private final LectureRepository lectureRepository;
    private final DoubtRepository doubtRepository;
    private final BookmarkRepository bookmarkRepository;
    private final QuizRepository quizRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final PollRepository pollRepository;
    private final PollVoteRepository pollVoteRepository;

    public TeacherController(UserRepository userRepository, SectionRepository sectionRepository,
                             LectureRepository lectureRepository, DoubtRepository doubtRepository,
                             BookmarkRepository bookmarkRepository, QuizRepository quizRepository,
                             QuizAttemptRepository quizAttemptRepository, PollRepository pollRepository,
                             PollVoteRepository pollVoteRepository) {
        this.userRepository = userRepository;
        this.sectionRepository = sectionRepository;
        this.lectureRepository = lectureRepository;
        this.doubtRepository = doubtRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.quizRepository = quizRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.pollRepository = pollRepository;
        this.pollVoteRepository = pollVoteRepository;
    }

    private User currentTeacher(HttpSession session) {
        if (!"TEACHER".equals(session.getAttribute("role"))) return null;
        Long userId = (Long) session.getAttribute("userId");
        return userRepository.findById(userId).orElse(null);
    }

    @GetMapping("/sections")
    public ResponseEntity<?> mySections(HttpSession session) {
        User teacher = currentTeacher(session);
        if (teacher == null) return ResponseEntity.status(403).body(Map.of("error", "Teachers only"));

        List<Map<String, Object>> result = sectionRepository.findAll().stream()
                .filter(s -> s.getTeachers().contains(teacher))
                .map(s -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", s.getId());
                    m.put("name", s.getName());
                    return m;
                }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/lectures")
    public ResponseEntity<?> myLectures(HttpSession session) {
        User teacher = currentTeacher(session);
        if (teacher == null) return ResponseEntity.status(403).body(Map.of("error", "Teachers only"));

        List<Map<String, Object>> result = lectureRepository.findByTeacherOrderByUploadedAtDesc(teacher).stream()
                .map(this::lectureToMap).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/lectures/upload")
    public ResponseEntity<?> uploadLecture(@RequestParam("file") MultipartFile file,
                                           @RequestParam("title") String title,
                                           @RequestParam("sectionId") Long sectionId,
                                           HttpSession session) {
        User teacher = currentTeacher(session);
        if (teacher == null) return ResponseEntity.status(403).body(Map.of("error", "Teachers only"));

        Optional<Section> sectionOpt = sectionRepository.findById(sectionId);
        if (sectionOpt.isEmpty() || !sectionOpt.get().getTeachers().contains(teacher)) {
            return ResponseEntity.status(403).body(Map.of("error", "You are not assigned to this section"));
        }

        try {
            String originalName = file.getOriginalFilename();
            String extension = (originalName != null && originalName.contains("."))
                    ? originalName.substring(originalName.lastIndexOf('.')) : ".webm";
            String storedName = UUID.randomUUID() + extension;

            Path target = Path.of("uploads", storedName);
            Files.createDirectories(target.getParent());
            file.transferTo(target);

            Lecture lecture = new Lecture();
            lecture.setTitle(title);
            lecture.setSection(sectionOpt.get());
            lecture.setTeacher(teacher);
            lecture.setFileName(storedName);
            lecture.setUploadedAt(LocalDateTime.now());
            lectureRepository.save(lecture);

            return ResponseEntity.ok(lectureToMap(lecture));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to save recording: " + e.getMessage()));
        }
    }

    @DeleteMapping("/lectures/{id}")
    public ResponseEntity<?> deleteLecture(@PathVariable Long id, HttpSession session) {
        User teacher = currentTeacher(session);
        if (teacher == null) return ResponseEntity.status(403).body(Map.of("error", "Teachers only"));

        Optional<Lecture> lectureOpt = lectureRepository.findById(id);
        if (lectureOpt.isEmpty() || !lectureOpt.get().getTeacher().getId().equals(teacher.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not your lecture"));
        }

        Lecture lecture = lectureOpt.get();

        // A lecture can have doubts, bookmarks, quizzes, and polls all pointing
        // back to it with required foreign keys. Clean those up first, in
        // dependency order, or the final delete gets silently rejected by the DB.
        doubtRepository.deleteAll(doubtRepository.findByLectureOrderByTimestampSecondsAsc(lecture));
        bookmarkRepository.deleteAll(bookmarkRepository.findByLecture(lecture));

        for (Quiz quiz : quizRepository.findByLectureOrderByCreatedAtAsc(lecture)) {
            quizAttemptRepository.deleteAll(quizAttemptRepository.findByQuiz(quiz));
            quizRepository.delete(quiz); // cascades quiz options automatically
        }
        for (Poll poll : pollRepository.findByLectureOrderByCreatedAtAsc(lecture)) {
            pollVoteRepository.deleteAll(pollVoteRepository.findByPoll(poll));
            pollRepository.delete(poll); // cascades poll options automatically
        }

        // Remove the video file from disk too, not just the DB row.
        try {
            Files.deleteIfExists(Path.of("uploads", lecture.getFileName()));
        } catch (IOException ignored) {
            // If the file is already gone for some reason, don't block the DB delete.
        }
        lectureRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Lecture deleted"));
    }

    @GetMapping("/lectures/{id}/doubts")
    public ResponseEntity<?> doubtsForLecture(@PathVariable Long id, HttpSession session) {
        User teacher = currentTeacher(session);
        if (teacher == null) return ResponseEntity.status(403).body(Map.of("error", "Teachers only"));

        Optional<Lecture> lectureOpt = lectureRepository.findById(id);
        if (lectureOpt.isEmpty() || !lectureOpt.get().getTeacher().getId().equals(teacher.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not your lecture"));
        }

        List<Map<String, Object>> result = doubtRepository.findByLectureOrderByTimestampSecondsAsc(lectureOpt.get())
                .stream().map(d -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", d.getId());
                    m.put("anonId", d.getStudent().getAnonId());
                    m.put("realUsername", d.getStudent().getUsername()); // teacher CAN resolve identity
                    m.put("realFullName", d.getStudent().getFullName());
                    m.put("timestampSeconds", d.getTimestampSeconds());
                    m.put("questionText", d.getQuestionText());
                    m.put("teacherReply", d.getTeacherReply());
                    return m;
                }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> lectureToMap(Lecture lecture) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", lecture.getId());
        m.put("title", lecture.getTitle());
        m.put("section", lecture.getSection().getName());
        m.put("videoUrl", "/uploads/" + lecture.getFileName());
        m.put("uploadedAt", lecture.getUploadedAt().toString());
        m.put("doubtCount", doubtRepository.findByLectureOrderByTimestampSecondsAsc(lecture).size());
        return m;
    }
}
