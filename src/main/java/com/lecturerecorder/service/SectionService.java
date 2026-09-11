package com.lecturerecorder.service;

import com.lecturerecorder.model.Section;
import com.lecturerecorder.model.User;
import com.lecturerecorder.repository.SectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class SectionService {

    private final SectionRepository sectionRepository;

    public SectionService(SectionRepository sectionRepository) {
        this.sectionRepository = sectionRepository;
    }

    @Transactional
    public List<Section> getAll() {
        List<Section> sections = sectionRepository.findAll();
        migrateLegacyAssignments(sections);
        return sections;
    }

    @Transactional
    public Optional<Section> getById(Long id) {
        Optional<Section> result = sectionRepository.findById(id);
        result.ifPresent(this::migrateLegacyAssignment);
        return result;
    }

    private void migrateLegacyAssignments(List<Section> sections) {
        for (Section section : sections) migrateLegacyAssignment(section);
    }

    private void migrateLegacyAssignment(Section section) {
        if (section.getTeacher() != null &&
                section.getTeachers().add(section.getTeacher())) {
            sectionRepository.save(section);
        }
    }

    public String addSection(String name) {
        if (sectionRepository.existsByName(name)) {
            return "A section named '" + name + "' already exists.";
        }
        sectionRepository.save(new Section(name));
        return null;
    }

    public void deleteSection(Long id) {
        sectionRepository.deleteById(id);
    }

    /** Add a teacher without removing any other teacher already assigned to the section. */
    @Transactional
    public void assignTeacher(Long sectionId, User teacher) {
        sectionRepository.findById(sectionId).ifPresent(s -> {
            s.getTeachers().add(teacher);
            // Preserve an old single-teacher assignment as well for backward compatibility.
            if (s.getTeacher() == null) {
                s.setTeacher(teacher);
            }
            sectionRepository.save(s);
        });
    }

    /** Remove only the selected teacher from the section. */
    @Transactional
    public void removeTeacher(Long sectionId, Long teacherId) {
        sectionRepository.findById(sectionId).ifPresent(s -> {
            s.getTeachers().removeIf(t -> t.getId().equals(teacherId));
            if (s.getTeacher() != null && s.getTeacher().getId().equals(teacherId)) {
                s.setTeacher(s.getTeachers().stream().findFirst().orElse(null));
            }
            sectionRepository.save(s);
        });
    }

    /** Clean up the many-to-many and legacy assignment before a teacher account is deleted. */
    @Transactional
    public void removeTeacherFromAllSections(Long teacherId) {
        for (Section s : sectionRepository.findAll()) {
            boolean changed = s.getTeachers().removeIf(t -> t.getId().equals(teacherId));
            if (s.getTeacher() != null && s.getTeacher().getId().equals(teacherId)) {
                s.setTeacher(s.getTeachers().stream().findFirst().orElse(null));
                changed = true;
            }
            if (changed) sectionRepository.save(s);
        }
    }

    public List<Section> getSectionsForTeacher(Long teacherId) {
        return sectionRepository.findAll().stream()
                .filter(s -> s.getTeachers().stream().anyMatch(t -> t.getId().equals(teacherId))
                        || (s.getTeacher() != null && s.getTeacher().getId().equals(teacherId)))
                .toList();
    }
}
