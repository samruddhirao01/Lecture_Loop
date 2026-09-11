package com.lecturerecorder.service;

import com.lecturerecorder.model.Role;
import com.lecturerecorder.model.Section;
import com.lecturerecorder.model.User;
import com.lecturerecorder.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Student self-registration. Account stays unverified until an admin approves it. */
    public String registerStudent(String username, String password, String fullName,
                                   String rollNumber, Section section) {
        if (userRepository.existsByUsername(username)) {
            return "That username is already taken.";
        }
        if (userRepository.existsByRollNumber(rollNumber)) {
            return "This roll number is already registered to an account. Each student may hold only one account.";
        }
        User student = new User();
        student.setUsername(username);
        student.setPassword(passwordEncoder.encode(password));
        student.setFullName(fullName);
        student.setRollNumber(rollNumber);
        student.setSection(section);
        student.setRole(Role.STUDENT);
        student.setVerified(false); // must be approved by admin before they can log in
        userRepository.save(student);
        return null; // no error
    }

    public User createTeacher(String username, String password, String fullName) {
        User teacher = new User();
        teacher.setUsername(username);
        teacher.setPassword(passwordEncoder.encode(password));
        teacher.setFullName(fullName);
        teacher.setRole(Role.TEACHER);
        teacher.setVerified(true);
        return userRepository.save(teacher);
    }

    public List<User> getPendingStudents() {
        return userRepository.findByRoleAndVerified(Role.STUDENT, false);
    }

    public List<User> getVerifiedStudents() {
        return userRepository.findByRoleAndVerified(Role.STUDENT, true);
    }

    public List<User> getTeachers() {
        return userRepository.findByRole(Role.TEACHER);
    }

    public List<User> getStudentsInSection(Section section) {
        return userRepository.findByRoleAndSection(Role.STUDENT, section)
                .stream().filter(User::isVerified).toList();
    }

    public void verifyStudent(Long userId) {
        userRepository.findById(userId).ifPresent(u -> {
            u.setVerified(true);
            userRepository.save(u);
        });
    }

    public void rejectStudent(Long userId) {
        userRepository.deleteById(userId);
    }

    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }

    public User getByUsername(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }

    public User getById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    /**
     * Self-service reset for students: proves identity with username + roll number
     * (no email server needed) before letting them set a new password.
     */
    public String resetStudentPassword(String username, String rollNumber, String newPassword) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || user.getRole() != Role.STUDENT) {
            return "No student account found with that username.";
        }
        if (user.getRollNumber() == null || !user.getRollNumber().equalsIgnoreCase(rollNumber.trim())) {
            return "Username and roll number don't match our records.";
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return null;
    }

    /** Logged-in user changing their own password - requires the current password. */
    public String changeOwnPassword(String username, String currentPassword, String newPassword) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            return "Current password is incorrect.";
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return null;
    }

    /** Admin resetting anyone's password directly - no current password needed. */
    public void adminResetPassword(Long userId, String newPassword) {
        userRepository.findById(userId).ifPresent(u -> {
            u.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(u);
        });
    }
}
