package com.lecturerecorder.service;

import com.lecturerecorder.model.*;
import com.lecturerecorder.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Service
public class RecordingService {

    private final RecordingRepository recordingRepository;
    private final DoubtRepository doubtRepository;
    private final BookmarkRepository bookmarkRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuizResponseRepository responseRepository;

    @Value("${app.upload-dir}")
    private String uploadDir;

    public RecordingService(RecordingRepository recordingRepository,
                            DoubtRepository doubtRepository,
                            BookmarkRepository bookmarkRepository,
                            QuizRepository quizRepository,
                            QuizQuestionRepository questionRepository,
                            QuizResponseRepository responseRepository) {
        this.recordingRepository = recordingRepository;
        this.doubtRepository = doubtRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.responseRepository = responseRepository;
    }

    public Recording upload(MultipartFile file, String title, Section section, User teacher) throws IOException {
        Files.createDirectories(Paths.get(uploadDir));

        String originalName = file.getOriginalFilename() == null ? "lecture" : file.getOriginalFilename();
        String extension = "";
        int dot = originalName.lastIndexOf('.');
        if (dot >= 0) extension = originalName.substring(dot);

        String storedName = UUID.randomUUID() + extension;
        Path destination = Paths.get(uploadDir).resolve(storedName);

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        }

        Recording recording = new Recording();
        recording.setTitle(title);
        recording.setStoredFileName(storedName);
        recording.setOriginalFileName(originalName);
        recording.setSection(section);
        recording.setUploadedBy(teacher);

        return recordingRepository.save(recording);
    }

    /**
     * Saves a browser MediaRecorder blob using the same recording storage used by
     * manual uploads. The browser normally sends WebM video/audio.
     */
    public Recording uploadBrowserRecording(MultipartFile file, String title,
                                            Section section, User teacher) throws IOException {
        return upload(file, title, section, teacher);
    }

    public List<Recording> getForSection(Section section) {
        return recordingRepository.findBySectionOrderByUploadedAtDesc(section);
    }

    public List<Recording> getByTeacher(Long teacherId) {
        return recordingRepository.findByUploadedByIdOrderByUploadedAtDesc(teacherId);
    }

    public Recording getById(Long id) {
        return recordingRepository.findById(id).orElse(null);
    }

    /**
     * Deletes a recording and all data attached to it (doubts, bookmarks,
     * quizzes, quiz questions and quiz responses), then removes the video file.
     */
    @Transactional
    public void deleteRecording(Long recordingId, Long teacherId) throws IOException {
        Recording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new IllegalArgumentException("Recording not found."));

        if (recording.getUploadedBy() == null || !recording.getUploadedBy().getId().equals(teacherId)) {
            throw new SecurityException("You can delete only your own recordings.");
        }

        // Remove data that references the recording first to avoid foreign-key errors.
        doubtRepository.deleteAll(doubtRepository.findByRecordingIdOrderByTimestampSecondsAsc(recordingId));
        bookmarkRepository.deleteAll(bookmarkRepository.findByRecordingId(recordingId));

        List<Quiz> quizzes = quizRepository.findByRecordingIdOrderByCreatedAtDesc(recordingId);
        for (Quiz quiz : quizzes) {
            List<QuizQuestion> questions = questionRepository.findByQuizIdOrderByIdAsc(quiz.getId());
            for (QuizQuestion question : questions) {
                responseRepository.deleteAll(responseRepository.findByQuestionId(question.getId()));
            }
            questionRepository.deleteAll(questions);
        }
        quizRepository.deleteAll(quizzes);

        recordingRepository.delete(recording);

        Path file = Paths.get(uploadDir).resolve(recording.getStoredFileName()).normalize();
        Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (file.toAbsolutePath().startsWith(uploadRoot)) {
            Files.deleteIfExists(file);
        }
    }
}
