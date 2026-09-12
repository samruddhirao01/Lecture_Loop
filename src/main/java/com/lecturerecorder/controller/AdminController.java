package com.lecturerecorder.controller;

import com.lecturerecorder.model.Role;
import com.lecturerecorder.model.Section;
import com.lecturerecorder.model.User;
import com.lecturerecorder.repository.*;
import com.lecturerecorder.util.AnonIdGenerator;
import com.lecturerecorder.util.PasswordUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final SectionRepository sectionRepository;
    private final LectureRepository lectureRepository;
    private final BookmarkRepository bookmarkRepository;
    private final DoubtRepository doubtRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final PollVoteRepository pollVoteRepository;

    public AdminController(UserRepository userRepository, SectionRepository sectionRepository,
                           LectureRepository lectureRepository, BookmarkRepository bookmarkRepository,
                           DoubtRepository doubtRepository, QuizAttemptRepository quizAttemptRepository,
                           PollVoteRepository pollVoteRepository) {
        this.userRepository = userRepository;
        this.sectionRepository = sectionRepository;
        this.lectureRepository = lectureRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.doubtRepository = doubtRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.pollVoteRepository = pollVoteRepository;
    }

    // Guards every endpoint in this controller -- only ADMIN may proceed.
    private boolean isAdmin(HttpSession session) {
        return "ADMIN".equals(session.getAttribute("role"));
    }

    // ---------- Sections ----------

    @GetMapping("/sections")
    public ResponseEntity<?> listSections(HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).body(Map.of("error", "Admins only"));
        List<Map<String, Object>> result = sectionRepository.findAll().stream().map(s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getId());
            m.put("name", s.getName());
            m.put("teacherNames", s.getTeachers().stream().map(User::getFullName).collect(Collectors.toList()));
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/sections")
    public ResponseEntity<?> createSection(@RequestBody Map<String, String> body, HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).body(Map.of("error", "Admins only"));
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Section name required"));
        }
        Section section = new Section(name.trim());
        sectionRepository.save(section);
        return ResponseEntity.ok(Map.of("id", section.getId(), "name", section.getName()));
    }

    @DeleteMapping("/sections/{id}")
    public ResponseEntity<?> deleteSection(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).body(Map.of("error", "Admins only"));

        Optional<Section> sectionOpt = sectionRepository.findById(id);
        if (sectionOpt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "Section not found"));
        Section section = sectionOpt.get();

        long lectureCount = lectureRepository.findBySectionOrderByUploadedAtDesc(section).size();
        if (lectureCount > 0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Cannot delete: " + lectureCount + " lecture(s) still belong to this section."));
        }

        long studentCount = userRepository.findByRole(Role.STUDENT).stream()
                .filter(s -> s.getSection() != null && s.getSection().getId().equals(id)).count();
        if (studentCount > 0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Cannot delete: " + studentCount + " student(s) are still assigned to this section."));
        }

        // Defensive cleanup: explicitly clear the teacher_sections join-table
        // rows for this section before deleting it, rather than relying on
        // JPA to infer that from the owning-side collection.
        section.getTeachers().clear();
        sectionRepository.save(section);

        sectionRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }

    // ---------- Teachers ----------

    @GetMapping("/teachers")
    public ResponseEntity<?> listTeachers(HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).body(Map.of("error", "Admins only"));
        List<Map<String, Object>> result = userRepository.findByRole(Role.TEACHER).stream().map(t -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", t.getId());
            m.put("username", t.getUsername());
            m.put("fullName", t.getFullName());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/teachers")
    public ResponseEntity<?> createTeacher(@RequestBody Map<String, String> body, HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).body(Map.of("error", "Admins only"));
        if (userRepository.findByUsername(body.get("username")).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username already exists"));
        }
        User teacher = new User();
        teacher.setUsername(body.get("username"));
        teacher.setPasswordHash(PasswordUtil.hash(body.get("password")));
        teacher.setFullName(body.get("fullName"));
        teacher.setRole(Role.TEACHER);
        userRepository.save(teacher);
        return ResponseEntity.ok(Map.of("id", teacher.getId(), "username", teacher.getUsername()));
    }

    @DeleteMapping("/teachers/{id}")
    public ResponseEntity<?> deleteTeacher(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).body(Map.of("error", "Admins only"));

        Optional<User> teacherOpt = userRepository.findById(id);
        if (teacherOpt.isEmpty() || teacherOpt.get().getRole() != Role.TEACHER) {
            return ResponseEntity.badRequest().body(Map.of("error", "Teacher not found"));
        }
        User teacher = teacherOpt.get();

        // This was the actual bug: the teacher was still linked to sections
        // via the teacher_sections table, so the database was rejecting the
        // delete outright and the UI had no way to know it failed.
        for (Section s : sectionRepository.findAll()) {
            if (s.getTeachers().remove(teacher)) {
                sectionRepository.save(s);
            }
        }

        long lectureCount = lectureRepository.findByTeacherOrderByUploadedAtDesc(teacher).size();
        if (lectureCount > 0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Cannot delete: this teacher has " + lectureCount +
                            " recorded lecture(s). Delete those recordings first (from the teacher's own login), then remove the teacher."));
        }

        userRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }

    // Assign (replace) which sections a teacher is responsible for
    @PostMapping("/teachers/{id}/sections")
    public ResponseEntity<?> assignSections(@PathVariable Long id, @RequestBody Map<String, List<Long>> body, HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).body(Map.of("error", "Admins only"));
        Optional<User> teacherOpt = userRepository.findById(id);
        if (teacherOpt.isEmpty() || teacherOpt.get().getRole() != Role.TEACHER) {
            return ResponseEntity.badRequest().body(Map.of("error", "Teacher not found"));
        }
        User teacher = teacherOpt.get();
        List<Long> sectionIds = body.get("sectionIds");

        // Remove this teacher from all sections first, then re-add to the chosen ones
        for (Section s : sectionRepository.findAll()) {
            s.getTeachers().remove(teacher);
            sectionRepository.save(s);
        }
        for (Long sid : sectionIds) {
            sectionRepository.findById(sid).ifPresent(s -> {
                s.getTeachers().add(teacher);
                sectionRepository.save(s);
            });
        }
        return ResponseEntity.ok(Map.of("message", "Sections updated"));
    }

    // ---------- Students ----------

    @GetMapping("/students")
    public ResponseEntity<?> listStudents(HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).body(Map.of("error", "Admins only"));
        // Only verified students show in the main roster -- pending ones show separately.
        List<Map<String, Object>> result = userRepository.findByRole(Role.STUDENT).stream()
                .filter(User::isVerified)
                .map(this::studentToMap).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // Self-registered students awaiting approval
    @GetMapping("/students/pending")
    public ResponseEntity<?> listPendingStudents(HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).body(Map.of("error", "Admins only"));
        List<Map<String, Object>> result = userRepository.findByRole(Role.STUDENT).stream()
                .filter(s -> !s.isVerified())
                .map(this::studentToMap).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/students/{id}/approve")
    public ResponseEntity<?> approveStudent(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).body(Map.of("error", "Admins only"));
        Optional<User> studentOpt = userRepository.findById(id);
        if (studentOpt.isEmpty() || studentOpt.get().getRole() != Role.STUDENT) {
            return ResponseEntity.badRequest().body(Map.of("error", "Student not found"));
        }
        User student = studentOpt.get();
        student.setVerified(true);
        userRepository.save(student);
        return ResponseEntity.ok(Map.of("message", "Approved"));
    }

    @PostMapping("/students/{id}/reject")
    public ResponseEntity<?> rejectStudent(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).body(Map.of("error", "Admins only"));
        Optional<User> studentOpt = userRepository.findById(id);
        if (studentOpt.isEmpty() || studentOpt.get().getRole() != Role.STUDENT) {
            return ResponseEntity.badRequest().body(Map.of("error", "Student not found"));
        }
        // Rejecting a pending registration just deletes the account outright.
        userRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Rejected"));
    }

    private Map<String, Object> studentToMap(User s) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", s.getId());
        m.put("username", s.getUsername());
        m.put("fullName", s.getFullName());
        m.put("rollNumber", s.getRollNumber());
        m.put("anonId", s.getAnonId());
        m.put("section", s.getSection() != null ? s.getSection().getName() : null);
        return m;
    }

    @PostMapping("/students")
    public ResponseEntity<?> createStudent(@RequestBody Map<String, String> body, HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).body(Map.of("error", "Admins only"));
        if (userRepository.findByUsername(body.get("username")).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username already exists"));
        }
        Long sectionId = Long.valueOf(body.get("sectionId"));
        Optional<Section> sectionOpt = sectionRepository.findById(sectionId);
        if (sectionOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Section not found"));
        }

        User student = new User();
        student.setUsername(body.get("username"));
        student.setPasswordHash(PasswordUtil.hash(body.get("password")));
        student.setFullName(body.get("fullName"));
        student.setRole(Role.STUDENT);
        student.setSection(sectionOpt.get());
        student.setAnonId(AnonIdGenerator.generate(userRepository));
        userRepository.save(student);

        return ResponseEntity.ok(Map.of(
                "id", student.getId(),
                "username", student.getUsername(),
                "anonId", student.getAnonId()
        ));
    }

    @DeleteMapping("/students/{id}")
    public ResponseEntity<?> deleteStudent(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).body(Map.of("error", "Admins only"));

        Optional<User> studentOpt = userRepository.findById(id);
        if (studentOpt.isEmpty() || studentOpt.get().getRole() != Role.STUDENT) {
            return ResponseEntity.badRequest().body(Map.of("error", "Student not found"));
        }
        User student = studentOpt.get();

        // A student may have posted doubts, bookmarks, quiz attempts, or poll
        // votes, all of which reference their user row. Clean those up first
        // so the delete doesn't get silently rejected by the database.
        bookmarkRepository.deleteAll(bookmarkRepository.findByStudent(student));
        doubtRepository.deleteAll(doubtRepository.findByStudent(student));
        quizAttemptRepository.deleteAll(quizAttemptRepository.findByStudent(student));
        pollVoteRepository.deleteAll(pollVoteRepository.findByStudent(student));

        userRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }
}
