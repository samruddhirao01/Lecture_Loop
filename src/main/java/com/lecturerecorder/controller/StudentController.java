package com.lecturerecorder.controller;

import com.lecturerecorder.model.Quiz;
import com.lecturerecorder.model.Recording;
import com.lecturerecorder.model.User;
import com.lecturerecorder.service.BookmarkService;
import com.lecturerecorder.service.DoubtService;
import com.lecturerecorder.service.QuizService;
import com.lecturerecorder.service.RecordingService;
import com.lecturerecorder.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/student")
public class StudentController {

    private final UserService userService;
    private final RecordingService recordingService;
    private final DoubtService doubtService;
    private final QuizService quizService;
    private final BookmarkService bookmarkService;

    public StudentController(UserService userService, RecordingService recordingService,
                              DoubtService doubtService, QuizService quizService,
                              BookmarkService bookmarkService) {
        this.userService = userService;
        this.recordingService = recordingService;
        this.doubtService = doubtService;
        this.quizService = quizService;
        this.bookmarkService = bookmarkService;
    }

    private User currentStudent(Authentication auth) {
        return userService.getByUsername(auth.getName());
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication auth, Model model) {
        User student = currentStudent(auth);
        model.addAttribute("student", student);
        model.addAttribute("recordings", recordingService.getForSection(student.getSection()));
        return "student-dashboard";
    }

    @GetMapping("/recordings/{id}")
    public String viewRecording(Authentication auth, @PathVariable Long id, Model model) {
        Recording recording = recordingService.getById(id);
        User student = currentStudent(auth);
        List<Quiz> quizzes = quizService.getForRecording(id);
        Map<Long, Boolean> attempted = new HashMap<>();
        for (Quiz q : quizzes) {
            attempted.put(q.getId(), quizService.hasAttempted(q.getId(), student.getId()));
        }
        model.addAttribute("recording", recording);
        model.addAttribute("markers", doubtService.getAnonymousMarkers(id));
        model.addAttribute("student", student);
        model.addAttribute("quizzes", quizzes);
        model.addAttribute("attempted", attempted);
        model.addAttribute("bookmarks", bookmarkService.getForStudent(id, student.getId()));
        return "student-recording";
    }

    @PostMapping("/recordings/{id}/doubt")
    public String addDoubt(Authentication auth, @PathVariable Long id,
                            @RequestParam int timestampSeconds,
                            @RequestParam(required = false) String comment) {
        Recording recording = recordingService.getById(id);
        User student = currentStudent(auth);
        if (recording != null) {
            doubtService.addDoubt(recording, student, timestampSeconds, comment);
        }
        return "redirect:/student/recordings/" + id + "?tagged";
    }

    // ---- Quizzes / Polls ----

    @GetMapping("/quizzes/{quizId}")
    public String takeQuiz(Authentication auth, @PathVariable Long quizId, Model model) {
        User student = currentStudent(auth);
        Quiz quiz = quizService.getQuiz(quizId);
        boolean attempted = quizService.hasAttempted(quizId, student.getId());

        model.addAttribute("quiz", quiz);
        model.addAttribute("student", student);
        model.addAttribute("attempted", attempted);

        if (attempted) {
            if (quiz.isPoll()) {
                model.addAttribute("pollResults", quizService.getPollResultsDetailed(quizId));
                return "student-quiz-result";
            } else {
                model.addAttribute("score", quizService.getScore(quizId, student.getId()));
                return "student-quiz-result";
            }
        }

        model.addAttribute("questions", quizService.getQuestions(quizId));
        return "student-quiz-take";
    }

    @PostMapping("/quizzes/{quizId}/submit")
    public String submitQuiz(Authentication auth, @PathVariable Long quizId,
                              @RequestParam Map<String, String> allParams) {
        User student = currentStudent(auth);
        Map<Long, Integer> answers = new HashMap<>();
        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            if (entry.getKey().startsWith("q_") && !entry.getValue().isBlank()) {
                Long questionId = Long.valueOf(entry.getKey().substring(2));
                answers.put(questionId, Integer.valueOf(entry.getValue()));
            }
        }
        quizService.submitAnswers(quizId, student, answers);
        return "redirect:/student/quizzes/" + quizId;
    }

    // ---- Bookmarks (private to each student) ----

    @PostMapping("/recordings/{id}/bookmark")
    public String addBookmark(Authentication auth, @PathVariable Long id,
                               @RequestParam int timestampSeconds,
                               @RequestParam(required = false) String note) {
        Recording recording = recordingService.getById(id);
        User student = currentStudent(auth);
        if (recording != null) {
            bookmarkService.addBookmark(recording, student, timestampSeconds, note);
        }
        return "redirect:/student/recordings/" + id + "?bookmarked";
    }

    @PostMapping("/bookmarks/{bookmarkId}/delete")
    public String deleteBookmark(Authentication auth, @PathVariable Long bookmarkId,
                                  @RequestParam Long recordingId) {
        User student = currentStudent(auth);
        bookmarkService.deleteBookmark(bookmarkId, student.getId());
        return "redirect:/student/recordings/" + recordingId;
    }
}
