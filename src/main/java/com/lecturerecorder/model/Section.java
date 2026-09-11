package com.lecturerecorder.model;

import jakarta.persistence.*;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "sections")
public class Section {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name; // e.g. TY-A, SY-B, FY-A

    // A class can have many teachers, and a teacher can teach many classes.
    // EAGER keeps the assigned-teacher list available to the Thymeleaf admin page
    // because this project intentionally uses spring.jpa.open-in-view=false.
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "section_teachers",
            joinColumns = @JoinColumn(name = "section_id"),
            inverseJoinColumns = @JoinColumn(name = "teacher_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"section_id", "teacher_id"})
    )
    private Set<User> teachers = new LinkedHashSet<>();

    // Kept for compatibility with older H2 databases created by previous versions.
    // New assignments use the many-to-many teachers collection above.
    @ManyToOne
    @JoinColumn(name = "teacher_id")
    private User teacher;

    public Section() {}

    public Section(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Set<User> getTeachers() { return teachers; }
    public void setTeachers(Set<User> teachers) { this.teachers = teachers; }

    public User getTeacher() { return teacher; }
    public void setTeacher(User teacher) { this.teacher = teacher; }
}
