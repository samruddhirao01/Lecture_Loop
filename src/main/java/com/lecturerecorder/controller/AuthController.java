package com.lecturerecorder.controller;

import com.lecturerecorder.model.Role;
import com.lecturerecorder.model.Section;
import com.lecturerecorder.model.User;
import com.lecturerecorder.repository.SectionRepository;
import com.lecturerecorder.repository.UserRepository;
import com.lecturerecorder.util.AnonIdGenerator;
import com.lecturerecorder.util.PasswordUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final SectionRepository sectionRepository;

    public AuthController(UserRepository userRepository, SectionRepository sectionRepository) {
        this.userRepository = userRepository;
        this.sectionRepository = sectionRepository;
    }

    // Public self-registration for students. Account is created but marked
    // unverified -- admin has to approve it before the student can log in.
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username required"));
        }
        if (userRepository.findByUsername(username).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username already exists"));
        }

        Long sectionId;
        try {
            sectionId = Long.valueOf(body.get("sectionId"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Select a section"));
        }
        Optional<Section> sectionOpt = sectionRepository.findById(sectionId);
        if (sectionOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Section not found"));
        }

        User student = new User();
        student.setUsername(username);
        student.setPasswordHash(PasswordUtil.hash(body.get("password")));
        student.setFullName(body.get("fullName"));
        student.setRole(Role.STUDENT);
        student.setSection(sectionOpt.get());
        student.setRollNumber(body.get("rollNumber"));
        student.setAnonId(AnonIdGenerator.generate(userRepository));
        student.setVerified(false); // pending admin approval
        userRepository.save(student);

        return ResponseEntity.ok(Map.of(
                "message", "Registered. Your account is pending admin verification before you can log in."
        ));
    }

    // Public - needed so the self-registration page can show a section dropdown
    @GetMapping("/public-sections")
    public ResponseEntity<?> publicSections() {
        return ResponseEntity.ok(sectionRepository.findAll().stream()
                .map(s -> Map.of("id", s.getId(), "name", s.getName()))
                .toList());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpSession session) {
        String username = body.get("username");
        String password = body.get("password");

        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty() || !PasswordUtil.matches(password, userOpt.get().getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid username or password"));
        }

        User user = userOpt.get();
        if (!user.isVerified()) {
            return ResponseEntity.status(403).body(Map.of("error", "Your account is pending admin verification"));
        }

        session.setAttribute("userId", user.getId());
        session.setAttribute("role", user.getRole().name());

        Map<String, Object> response = new HashMap<>();
        response.put("role", user.getRole().name());
        response.put("fullName", user.getFullName());
        response.put("username", user.getUsername());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));
        }
        Optional<User> userOpt = userRepository.findById((Long) userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));
        }
        User user = userOpt.get();
        Map<String, Object> response = new HashMap<>();
        response.put("role", user.getRole().name());
        response.put("fullName", user.getFullName());
        response.put("username", user.getUsername());
        return ResponseEntity.ok(response);
    }
}
