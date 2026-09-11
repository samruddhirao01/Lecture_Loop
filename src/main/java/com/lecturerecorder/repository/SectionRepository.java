package com.lecturerecorder.repository;

import com.lecturerecorder.model.Section;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SectionRepository extends JpaRepository<Section, Long> {
    Optional<Section> findByName(String name);
    boolean existsByName(String name);
}
