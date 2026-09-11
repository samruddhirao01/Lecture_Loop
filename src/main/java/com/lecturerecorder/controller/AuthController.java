package com.lecturerecorder.controller;

import com.lecturerecorder.model.Section;
import com.lecturerecorder.service.SectionService;
import com.lecturerecorder.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    private final UserService userService;
    private final SectionService sectionService;

    public AuthController(UserService userService, SectionService sectionService) {
        this.userService = userService;
        this.sectionService = sectionService;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("sections", sectionService.getAll());
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username,
                            @RequestParam String password,
                            @RequestParam String fullName,
                            @RequestParam String rollNumber,
                            @RequestParam Long sectionId,
                            Model model) {

        Section section = sectionService.getById(sectionId).orElse(null);
        if (section == null) {
            model.addAttribute("error", "Please choose a valid class/section.");
            model.addAttribute("sections", sectionService.getAll());
            return "register";
        }

        String error = userService.registerStudent(username, password, fullName, rollNumber, section);
        if (error != null) {
            model.addAttribute("error", error);
            model.addAttribute("sections", sectionService.getAll());
            return "register";
        }

        return "redirect:/login?registered";
    }

    // ---- Forgot password (students only - self-service via roll number) ----

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam String username,
                                  @RequestParam String rollNumber,
                                  @RequestParam String newPassword,
                                  @RequestParam String confirmPassword,
                                  Model model) {
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "New password and confirm password don't match.");
            return "forgot-password";
        }
        String error = userService.resetStudentPassword(username, rollNumber, newPassword);
        if (error != null) {
            model.addAttribute("error", error);
            return "forgot-password";
        }
        return "redirect:/login?resetdone";
    }
}
