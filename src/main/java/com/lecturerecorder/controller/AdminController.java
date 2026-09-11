package com.lecturerecorder.controller;

import com.lecturerecorder.model.Role;
import com.lecturerecorder.model.User;
import com.lecturerecorder.service.SectionService;
import com.lecturerecorder.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserService userService;
    private final SectionService sectionService;

    public AdminController(UserService userService, SectionService sectionService) {
        this.userService = userService;
        this.sectionService = sectionService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("sections", sectionService.getAll());
        model.addAttribute("teachers", userService.getTeachers());
        model.addAttribute("pendingStudents", userService.getPendingStudents());
        model.addAttribute("verifiedStudents", userService.getVerifiedStudents());
        model.addAttribute("role", Role.TEACHER);
        return "admin-dashboard";
    }

    // ---- Sections ----

    @PostMapping("/sections/add")
    public String addSection(@RequestParam String name, Model model) {
        sectionService.addSection(name.trim());
        return "redirect:/admin/dashboard";
    }

    @PostMapping("/sections/{id}/delete")
    public String deleteSection(@PathVariable Long id) {
        sectionService.deleteSection(id);
        return "redirect:/admin/dashboard";
    }

    @PostMapping("/sections/{id}/assign-teacher")
    public String assignTeacher(@PathVariable Long id, @RequestParam Long teacherId) {
        User teacher = userService.getTeachers().stream()
                .filter(t -> t.getId().equals(teacherId)).findFirst().orElse(null);
        if (teacher != null) {
            sectionService.assignTeacher(id, teacher);
        }
        return "redirect:/admin/dashboard";
    }

    @PostMapping("/sections/{id}/remove-teacher")
    public String removeTeacher(@PathVariable Long id, @RequestParam Long teacherId) {
        sectionService.removeTeacher(id, teacherId);
        return "redirect:/admin/dashboard";
    }

    // ---- Teachers ----

    @PostMapping("/teachers/add")
    public String addTeacher(@RequestParam String username,
                              @RequestParam String password,
                              @RequestParam String fullName) {
        userService.createTeacher(username, password, fullName);
        return "redirect:/admin/dashboard";
    }

    // ---- Students ----

    @PostMapping("/students/{id}/verify")
    public String verifyStudent(@PathVariable Long id) {
        userService.verifyStudent(id);
        return "redirect:/admin/dashboard";
    }

    @PostMapping("/students/{id}/reject")
    public String rejectStudent(@PathVariable Long id) {
        userService.rejectStudent(id);
        return "redirect:/admin/dashboard";
    }

    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user != null && user.getRole() == Role.TEACHER) {
            sectionService.removeTeacherFromAllSections(id);
        }
        userService.deleteUser(id);
        return "redirect:/admin/dashboard";
    }

    @PostMapping("/users/{id}/reset-password")
    public String resetPassword(@PathVariable Long id, @RequestParam String newPassword) {
        userService.adminResetPassword(id, newPassword);
        return "redirect:/admin/dashboard";
    }
}
