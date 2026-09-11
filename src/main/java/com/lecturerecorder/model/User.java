package com.lecturerecorder.model;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password; // BCrypt hashed

    @Column(nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // Only meaningful for STUDENT accounts - the college roll number / ID.
    // Unique so the same real student can never hold two accounts.
    @Column(unique = true)
    private String rollNumber;

    // Section the student belongs to, or the section a teacher primarily manages
    @ManyToOne
    @JoinColumn(name = "section_id")
    private Section section;

    // STUDENT accounts start unverified and cannot log in until an admin approves them.
    // TEACHER and ADMIN accounts are always created pre-verified.
    @Column(nullable = false)
    private boolean verified;

    public User() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getRollNumber() { return rollNumber; }
    public void setRollNumber(String rollNumber) { this.rollNumber = rollNumber; }

    public Section getSection() { return section; }
    public void setSection(Section section) { this.section = section; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
}
