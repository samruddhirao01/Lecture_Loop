package com.lecturerecorder.model;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // College ID for students, chosen username for admin/teacher
    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // Only populated for STUDENT role. This is what teachers/other students see
    // instead of the real name -- kept stable so patterns (e.g. repeated doubts
    // from the same student) are visible without revealing identity.
    private String anonId;

    // Only populated for STUDENT role -- which section they belong to.
    @ManyToOne
    @JoinColumn(name = "section_id")
    private Section section;

    // Only populated for STUDENT role -- college roll number, entered at self-registration.
    private String rollNumber;

    // Only relevant for STUDENT role. Admin/teacher created students are auto-verified.
    // Self-registered students start as false (pending) until an admin approves them.
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean verified = true;

    public User() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getAnonId() { return anonId; }
    public void setAnonId(String anonId) { this.anonId = anonId; }

    public Section getSection() { return section; }
    public void setSection(Section section) { this.section = section; }

    public String getRollNumber() { return rollNumber; }
    public void setRollNumber(String rollNumber) { this.rollNumber = rollNumber; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
}
