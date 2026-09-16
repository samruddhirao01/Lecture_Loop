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
@RequestMapping("/api/quizzes")
public class QuizController {

    private final UserRepository userRepository;
    private final LectureRepository lectureRepository;
    private final QuizRepository quizRepository;
    private final QuizOptionRepository quizOptionRepository;
    private final QuizAttemptRepository quizAttemptRepository;

    public QuizController(UserRepository userRepository, LectureRepository lectureRepository,
                           QuizRepository quizRepository, QuizOptionRepository quizOptionRepository,
                           QuizAttemptRepository quizAttemptRepository) {
        this.userRepository = userRepository;
        this.lectureRepository = lectureRepository;
        this.quizRepository = quizRepository;
        this.quizOptionRepository = quizOptionRepository;
        this.quizAttemptRepository = quizAttemptRepository;
    }

    private User currentUser(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return null;
        return userRepository.findById(userId).orElse(null);
    }

    // Teacher creates a quiz: { lectureId, question, options: [{text, correct}, ...] } (2-4 options)
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
        List<Map<String, Object>> optionsInput = (List<Map<String, Object>>) body.get("options");
        if (optionsInput == null || optionsInput.size() < 2 || optionsInput.size() > 4) {
            return ResponseEntity.badRequest().body(Map.of("error", "Provide between 2 and 4 options"));
        }
        boolean hasCorrect = optionsInput.stream().anyMatch(o -> Boolean.TRUE.equals(o.get("correct")));
        if (!hasCorrect) {
            return ResponseEntity.badRequest().body(Map.of("error", "Mark one option as correct"));
        }

        Quiz quiz = new Quiz();
        quiz.setLecture(lectureOpt.get());
        quiz.setQuestion(body.get("question").toString());
        quiz.setCreatedAt(LocalDateTime.now());
        quizRepository.save(quiz);

        for (Map<String, Object> o : optionsInput) {
            QuizOption option = new QuizOption();
            option.setQuiz(quiz);
            option.setOptionText(o.get("text").toString());
            option.setCorrect(Boolean.TRUE.equals(o.get("correct")));
            quizOptionRepository.save(option);
        }

        return ResponseEntity.ok(Map.of("id", quiz.getId(), "message", "Quiz created"));
    }

    // List quizzes for a lecture. Students never see which option is correct
    // until after they've submitted their attempt.
    @GetMapping
    public ResponseEntity<?> listForLecture(@RequestParam Long lectureId, HttpSession session) {
        User user = currentUser(session);
        if (user == null) return ResponseEntity.status(403).body(Map.of("error", "Not logged in"));

        Optional<Lecture> lectureOpt = lectureRepository.findById(lectureId);
        if (lectureOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "Lecture not found"));

        boolean isTeacher = "TEACHER".equals(session.getAttribute("role"));
        boolean isStudent = "STUDENT".equals(session.getAttribute("role"));
        if (isStudent && (user.getSection() == null
                || !lectureOpt.get().getSection().getId().equals(user.getSection().getId()))) {
            return ResponseEntity.status(403).body(Map.of("error", "Not your section's lecture"));
        }
        List<Map<String, Object>> result = quizRepository.findByLectureOrderByCreatedAtAsc(lectureOpt.get())
                .stream().map(q -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", q.getId());
                    m.put("question", q.getQuestion());

                    Optional<QuizAttempt> myAttempt = "STUDENT".equals(session.getAttribute("role"))
                            ? quizAttemptRepository.findByQuizAndStudent(q, user) : Optional.empty();

                    List<Map<String, Object>> options = q.getOptions().stream().map(o -> {
                        Map<String, Object> om = new HashMap<>();
                        om.put("id", o.getId());
                        om.put("text", o.getOptionText());
                        // Only reveal correctness to the teacher, or to a student who already attempted.
                        if (isTeacher || myAttempt.isPresent()) {
                            om.put("correct", o.isCorrect());
                        }
                        return om;
                    }).collect(Collectors.toList());
                    m.put("options", options);
                    m.put("alreadyAttempted", myAttempt.isPresent());
                    myAttempt.ifPresent(a -> m.put("yourSelectedOptionId", a.getSelectedOption().getId()));
                    return m;
                }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // Student submits an attempt
    @PostMapping("/{id}/attempt")
    public ResponseEntity<?> attempt(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpSession session) {
        if (!"STUDENT".equals(session.getAttribute("role"))) {
            return ResponseEntity.status(403).body(Map.of("error", "Students only"));
        }
        User student = currentUser(session);

        Optional<Quiz> quizOpt = quizRepository.findById(id);
        if (quizOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "Quiz not found"));
        Quiz quiz = quizOpt.get();

        if (student.getSection() == null
                || !quiz.getLecture().getSection().getId().equals(student.getSection().getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not your section's quiz"));
        }

        if (quizAttemptRepository.findByQuizAndStudent(quiz, student).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "You already attempted this quiz"));
        }

        Long selectedOptionId = Long.valueOf(body.get("optionId").toString());
        QuizOption selected = quiz.getOptions().stream()
                .filter(o -> o.getId().equals(selectedOptionId)).findFirst().orElse(null);
        if (selected == null) return ResponseEntity.badRequest().body(Map.of("error", "Invalid option"));

        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuiz(quiz);
        attempt.setStudent(student);
        attempt.setSelectedOption(selected);
        attempt.setCorrect(selected.isCorrect());
        attempt.setSubmittedAt(LocalDateTime.now());
        quizAttemptRepository.save(attempt);

        return ResponseEntity.ok(Map.of("correct", selected.isCorrect()));
    }

    // Teacher: student-wise results for a quiz
    @GetMapping("/{id}/results")
    public ResponseEntity<?> results(@PathVariable Long id, HttpSession session) {
        if (!"TEACHER".equals(session.getAttribute("role"))) {
            return ResponseEntity.status(403).body(Map.of("error", "Teachers only"));
        }
        User teacher = currentUser(session);

        Optional<Quiz> quizOpt = quizRepository.findById(id);
        if (quizOpt.isEmpty() || !quizOpt.get().getLecture().getTeacher().getId().equals(teacher.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not your quiz"));
        }
        Quiz quiz = quizOpt.get();

        List<QuizAttempt> attempts = quizAttemptRepository.findByQuiz(quiz);
        long totalAnswered = attempts.size();
        long totalCorrect = attempts.stream().filter(QuizAttempt::isCorrect).count();

        List<Map<String, Object>> studentWise = attempts.stream().map(a -> {
            Map<String, Object> m = new HashMap<>();
            m.put("studentUsername", a.getStudent().getUsername());
            m.put("studentFullName", a.getStudent().getFullName());
            m.put("selectedOption", a.getSelectedOption().getOptionText());
            m.put("correct", a.isCorrect());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("totalAnswered", totalAnswered);
        result.put("totalCorrect", totalCorrect);
        result.put("students", studentWise);
        return ResponseEntity.ok(result);
    }
}
