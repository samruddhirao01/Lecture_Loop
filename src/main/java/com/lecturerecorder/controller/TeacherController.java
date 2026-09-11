package com.lecturerecorder.controller;

import com.lecturerecorder.model.Quiz;
import com.lecturerecorder.model.Recording;
import com.lecturerecorder.model.Section;
import com.lecturerecorder.model.User;
import com.lecturerecorder.service.DoubtService;
import com.lecturerecorder.service.QuizService;
import com.lecturerecorder.service.RecordingService;
import com.lecturerecorder.service.SectionService;
import com.lecturerecorder.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/teacher")
public class TeacherController {

    private final UserService userService;
    private final SectionService sectionService;
    private final RecordingService recordingService;
    private final DoubtService doubtService;
    private final QuizService quizService;

    public TeacherController(UserService userService, SectionService sectionService,
                              RecordingService recordingService, DoubtService doubtService,
                              QuizService quizService) {
        this.userService = userService;
        this.sectionService = sectionService;
        this.recordingService = recordingService;
        this.doubtService = doubtService;
        this.quizService = quizService;
    }

    private User currentTeacher(Authentication auth) {
        return userService.getByUsername(auth.getName());
    }

    private boolean isAssignedSection(Section section, User teacher) {
        return section.getTeachers().stream().anyMatch(t -> t.getId().equals(teacher.getId()))
                || (section.getTeacher() != null && section.getTeacher().getId().equals(teacher.getId()));
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication auth, Model model) {
        User teacher = currentTeacher(auth);
        List<Section> mySections = sectionService.getSectionsForTeacher(teacher.getId());

        model.addAttribute("teacher", teacher);
        model.addAttribute("sections", mySections);
        model.addAttribute("recordings", recordingService.getByTeacher(teacher.getId()));
        return "teacher-dashboard";
    }

    @GetMapping("/sections/{id}/roster")
    public String roster(Authentication auth, @PathVariable Long id, Model model) {
        Section section = sectionService.getById(id).orElse(null);
        model.addAttribute("section", section);
        model.addAttribute("students", section == null ? List.of() : userService.getStudentsInSection(section));
        model.addAttribute("teacher", currentTeacher(auth));
        return "teacher-roster";
    }

    @PostMapping("/recordings/upload")
    public String upload(Authentication auth,
                          @RequestParam String title,
                          @RequestParam Long sectionId,
                          @RequestParam MultipartFile file,
                          Model model) throws IOException {
        User teacher = currentTeacher(auth);
        Section section = sectionService.getById(sectionId).orElse(null);
        if (section != null && !file.isEmpty() && isAssignedSection(section, teacher)) {
            recordingService.upload(file, title, section, teacher);
        }
        return "redirect:/teacher/dashboard";
    }

    @PostMapping("/recordings/record")
    @ResponseBody
    public java.util.Map<String, Object> saveBrowserRecording(
            Authentication auth,
            @RequestParam String title,
            @RequestParam Long sectionId,
            @RequestParam MultipartFile file) throws IOException {

        User teacher = currentTeacher(auth);
        Section section = sectionService.getById(sectionId).orElse(null);

        if (section == null || file.isEmpty()) {
            throw new IllegalArgumentException("Recording or section is missing.");
        }
        if (!isAssignedSection(section, teacher)) {
            throw new SecurityException("You are not assigned to this section.");
        }

        Recording recording = recordingService.uploadBrowserRecording(file, title, section, teacher);

        return java.util.Map.of(
                "success", true,
                "recordingId", recording.getId(),
                "message", "Recording uploaded successfully."
        );
    }

    @PostMapping("/recordings/{id}/delete")
    public String deleteRecording(Authentication auth, @PathVariable Long id) throws IOException {
        User teacher = currentTeacher(auth);
        recordingService.deleteRecording(id, teacher.getId());
        return "redirect:/teacher/dashboard";
    }

    @GetMapping("/recordings/{id}/doubts")
    public String viewDoubts(Authentication auth, @PathVariable Long id, Model model) {
        Recording recording = recordingService.getById(id);
        model.addAttribute("recording", recording);
        model.addAttribute("doubts", doubtService.getDoubtsForTeacher(id));
        model.addAttribute("hotThreshold", doubtService.getHotThreshold());
        model.addAttribute("teacher", currentTeacher(auth));
        return "teacher-doubts";
    }

    // ---- Quizzes / Polls ----

    @GetMapping("/recordings/{recordingId}/quizzes")
    public String quizzesForRecording(Authentication auth, @PathVariable Long recordingId, Model model) {
        model.addAttribute("recording", recordingService.getById(recordingId));
        model.addAttribute("quizzes", quizService.getForRecording(recordingId));
        model.addAttribute("teacher", currentTeacher(auth));
        return "teacher-recording-quizzes";
    }

    @GetMapping("/recordings/{recordingId}/quizzes/new")
    public String newQuizForm(Authentication auth, @PathVariable Long recordingId, Model model) {
        model.addAttribute("recording", recordingService.getById(recordingId));
        model.addAttribute("teacher", currentTeacher(auth));
        return "teacher-quiz-new";
    }

    @PostMapping("/recordings/{recordingId}/quizzes/create")
    public String createQuiz(@PathVariable Long recordingId,
                              @RequestParam String title,
                              @RequestParam(defaultValue = "false") boolean poll) {
        Recording recording = recordingService.getById(recordingId);
        Quiz quiz = quizService.createQuiz(recording, title, poll);
        return "redirect:/teacher/quizzes/" + quiz.getId();
    }

    @GetMapping("/quizzes/{quizId}")
    public String manageQuiz(Authentication auth, @PathVariable Long quizId, Model model) {
        Quiz quiz = quizService.getQuiz(quizId);
        model.addAttribute("quiz", quiz);
        model.addAttribute("questions", quizService.getQuestions(quizId));
        model.addAttribute("teacher", currentTeacher(auth));
        return "teacher-quiz-manage";
    }

    @PostMapping("/quizzes/{quizId}/questions/add")
    public String addQuestion(@PathVariable Long quizId,
                               @RequestParam String questionText,
                               @RequestParam String option1,
                               @RequestParam String option2,
                               @RequestParam(required = false) String option3,
                               @RequestParam(required = false) String option4,
                               @RequestParam(required = false) Integer correctOptionIndex) {
        Quiz quiz = quizService.getQuiz(quizId);
        quizService.addQuestion(quiz, questionText, option1, option2, option3, option4, correctOptionIndex);
        return "redirect:/teacher/quizzes/" + quizId;
    }

    @GetMapping("/quizzes/{quizId}/results")
    public String quizResults(Authentication auth, @PathVariable Long quizId, Model model) {
        Quiz quiz = quizService.getQuiz(quizId);
        model.addAttribute("quiz", quiz);
        model.addAttribute("questions", quizService.getQuestions(quizId));
        model.addAttribute("teacher", currentTeacher(auth));
        if (quiz.isPoll()) {
            model.addAttribute("pollResults", quizService.getPollResultsDetailed(quizId));
        } else {
            model.addAttribute("studentScores", quizService.getAllStudentScores(quizId));
        }
        return "teacher-quiz-results";
    }
}
