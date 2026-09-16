package com.lecturerecorder.repository;

import com.lecturerecorder.model.Section;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionRepository extends JpaRepository<Section, Long> {
}
