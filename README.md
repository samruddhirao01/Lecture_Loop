# Lecture Recorder

Spring Boot + Thymeleaf lecture recording platform.

## Teacher/Class assignment

- One class/section can have multiple teachers.
- One teacher can be assigned to multiple classes/sections.
- Admin assigns teachers to sections from the Admin Dashboard.
- A teacher sees all assigned sections in the Teacher Dashboard.
- Before recording or uploading a lecture, the teacher selects the class/section for that lecture.
- The recording is saved against the selected section, so SY and TY lectures from the same teacher stay separated.
- Teachers can remove their own uploaded recordings; related doubts, bookmarks and quizzes are removed with the recording.

## Browser recording

The Teacher Dashboard uses the browser MediaRecorder API to capture the shared screen and microphone, then uploads the resulting WebM recording to the Spring Boot server.

For real deployment, use HTTPS because browser screen/microphone capture requires a secure context (localhost is allowed for development).
