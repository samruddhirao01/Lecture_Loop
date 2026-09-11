package com.lecturerecorder.repository;

import com.lecturerecorder.model.Role;
import com.lecturerecorder.model.Section;
import com.lecturerecorder.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByRollNumber(String rollNumber);
    List<User> findByRole(Role role);
    List<User> findByRoleAndVerified(Role role, boolean verified);
    List<User> findByRoleAndSection(Role role, Section section);
}
