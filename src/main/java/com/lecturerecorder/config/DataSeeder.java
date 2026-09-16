package com.lecturerecorder.config;

import com.lecturerecorder.model.Role;
import com.lecturerecorder.model.User;
import com.lecturerecorder.repository.UserRepository;
import com.lecturerecorder.util.PasswordUtil;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Runs once at startup:
 *  - makes sure the /uploads folder exists on disk
 *  - creates a default admin account if none exists yet, so the demo
 *    can start immediately without a manual DB setup step.
 *
 * Default admin login: username = admin, password = admin123
 * (Change this in a real deployment.)
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;

    public DataSeeder(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        File uploadsDir = new File("uploads");
        if (!uploadsDir.exists()) {
            uploadsDir.mkdirs();
        }

        if (userRepository.findByRole(Role.ADMIN).isEmpty()) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPasswordHash(PasswordUtil.hash("admin123"));
            admin.setFullName("Administrator");
            admin.setRole(Role.ADMIN);
            userRepository.save(admin);
            System.out.println("==============================================");
            System.out.println("Default admin created -> username: admin / password: admin123");
            System.out.println("==============================================");
        }
    }
}
