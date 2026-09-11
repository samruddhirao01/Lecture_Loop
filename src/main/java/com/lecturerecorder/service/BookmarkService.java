package com.lecturerecorder.service;

import com.lecturerecorder.model.Bookmark;
import com.lecturerecorder.model.Recording;
import com.lecturerecorder.model.User;
import com.lecturerecorder.repository.BookmarkRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;

    public BookmarkService(BookmarkRepository bookmarkRepository) {
        this.bookmarkRepository = bookmarkRepository;
    }

    public void addBookmark(Recording recording, User student, int timestampSeconds, String note) {
        Bookmark bookmark = new Bookmark();
        bookmark.setRecording(recording);
        bookmark.setStudent(student);
        bookmark.setTimestampSeconds(timestampSeconds);
        bookmark.setNote(note);
        bookmarkRepository.save(bookmark);
    }

    public List<Bookmark> getForStudent(Long recordingId, Long studentId) {
        return bookmarkRepository.findByRecordingIdAndStudentIdOrderByTimestampSecondsAsc(recordingId, studentId);
    }

    /** Only deletes if the bookmark actually belongs to this student - prevents deleting someone else's by guessing an id. */
    public void deleteBookmark(Long bookmarkId, Long studentId) {
        bookmarkRepository.findById(bookmarkId).ifPresent(b -> {
            if (b.getStudent().getId().equals(studentId)) {
                bookmarkRepository.deleteById(bookmarkId);
            }
        });
    }
}
