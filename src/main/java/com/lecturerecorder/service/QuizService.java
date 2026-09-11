package com.lecturerecorder.service;

import com.lecturerecorder.model.*;
import com.lecturerecorder.repository.QuizQuestionRepository;
import com.lecturerecorder.repository.QuizRepository;
import com.lecturerecorder.repository.QuizResponseRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class QuizService {

    private final QuizRepository quizRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuizResponseRepository responseRepository;

    public QuizService(QuizRepository quizRepository, QuizQuestionRepository questionRepository,
                        QuizResponseRepository responseRepository) {
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.responseRepository = responseRepository;
    }

    public Quiz createQuiz(Recording recording, String title, boolean isPoll) {
        Quiz quiz = new Quiz();
        quiz.setRecording(recording);
        quiz.setTitle(title);
        quiz.setPoll(isPoll);
        return quizRepository.save(quiz);
    }

    public QuizQuestion addQuestion(Quiz quiz, String questionText, String opt1, String opt2,
                                     String opt3, String opt4, Integer correctOptionIndex) {
        QuizQuestion q = new QuizQuestion();
        q.setQuiz(quiz);
        q.setQuestionText(questionText);
        q.setOption1(opt1);
        q.setOption2(opt2);
        q.setOption3(opt3 == null || opt3.isBlank() ? null : opt3);
        q.setOption4(opt4 == null || opt4.isBlank() ? null : opt4);
        q.setCorrectOptionIndex(quiz.isPoll() ? null : correctOptionIndex);
        return questionRepository.save(q);
    }

    public Quiz getQuiz(Long id) {
        return quizRepository.findById(id).orElse(null);
    }

    public List<Quiz> getForRecording(Long recordingId) {
        return quizRepository.findByRecordingIdOrderByCreatedAtDesc(recordingId);
    }

    public List<QuizQuestion> getQuestions(Long quizId) {
        return questionRepository.findByQuizIdOrderByIdAsc(quizId);
    }

    public boolean hasAttempted(Long quizId, Long studentId) {
        List<QuizQuestion> questions = getQuestions(quizId);
        if (questions.isEmpty()) return false;
        List<Long> questionIds = questions.stream().map(QuizQuestion::getId).toList();
        return !responseRepository.findByQuestionIdInAndStudentId(questionIds, studentId).isEmpty();
    }

    /** answers: questionId -> selected option index (1-4). Skips silently if the student already attempted this quiz. */
    public void submitAnswers(Long quizId, User student, Map<Long, Integer> answers) {
        if (hasAttempted(quizId, student.getId())) return;
        for (Map.Entry<Long, Integer> entry : answers.entrySet()) {
            QuizQuestion question = questionRepository.findById(entry.getKey()).orElse(null);
            if (question == null) continue;
            QuizResponse response = new QuizResponse();
            response.setQuestion(question);
            response.setStudent(student);
            response.setSelectedOptionIndex(entry.getValue());
            responseRepository.save(response);
        }
    }

    /** Only meaningful for graded quizzes (not polls). */
    public ScoreResult getScore(Long quizId, Long studentId) {
        List<QuizQuestion> questions = getQuestions(quizId);
        int correct = 0;
        int answered = 0;
        for (QuizQuestion q : questions) {
            Optional<QuizResponse> resp = responseRepository.findByQuestionIdAndStudentId(q.getId(), studentId);
            if (resp.isPresent()) {
                answered++;
                if (q.getCorrectOptionIndex() != null &&
                        q.getCorrectOptionIndex().equals(resp.get().getSelectedOptionIndex())) {
                    correct++;
                }
            }
        }
        return new ScoreResult(correct, answered, questions.size());
    }

    /** For the teacher's quiz results page (graded mode): one row per student who attempted, real identity included. */
    public List<StudentScore> getAllStudentScores(Long quizId) {
        List<QuizQuestion> questions = getQuestions(quizId);
        Map<Long, User> studentsById = new LinkedHashMap<>();
        for (QuizQuestion q : questions) {
            for (QuizResponse r : responseRepository.findByQuestionId(q.getId())) {
                studentsById.putIfAbsent(r.getStudent().getId(), r.getStudent());
            }
        }
        List<StudentScore> result = new ArrayList<>();
        for (User student : studentsById.values()) {
            ScoreResult score = getScore(quizId, student.getId());
            result.add(new StudentScore(student, score.correct(), score.total()));
        }
        return result;
    }

    /** For poll mode: per question, how many votes each option got. No student identity attached. */
    public Map<Long, Map<Integer, Integer>> getPollAggregate(Long quizId) {
        Map<Long, Map<Integer, Integer>> result = new LinkedHashMap<>();
        for (QuizQuestion q : getQuestions(quizId)) {
            Map<Integer, Integer> counts = new TreeMap<>();
            for (QuizResponse r : responseRepository.findByQuestionId(q.getId())) {
                counts.merge(r.getSelectedOptionIndex(), 1, Integer::sum);
            }
            result.put(q.getId(), counts);
        }
        return result;
    }

    /** Template-friendly version of poll results: each question paired with its option labels + vote counts. */
    public List<QuestionPollResult> getPollResultsDetailed(Long quizId) {
        List<QuestionPollResult> results = new ArrayList<>();
        for (QuizQuestion q : getQuestions(quizId)) {
            Map<Integer, Integer> counts = new TreeMap<>();
            for (QuizResponse r : responseRepository.findByQuestionId(q.getId())) {
                counts.merge(r.getSelectedOptionIndex(), 1, Integer::sum);
            }
            List<OptionCount> options = new ArrayList<>();
            addOptionIfPresent(options, 1, q.getOption1(), counts);
            addOptionIfPresent(options, 2, q.getOption2(), counts);
            addOptionIfPresent(options, 3, q.getOption3(), counts);
            addOptionIfPresent(options, 4, q.getOption4(), counts);
            results.add(new QuestionPollResult(q, options));
        }
        return results;
    }

    private void addOptionIfPresent(List<OptionCount> list, int index, String label, Map<Integer, Integer> counts) {
        if (label != null && !label.isBlank()) {
            list.add(new OptionCount(index, label, counts.getOrDefault(index, 0)));
        }
    }

    public record ScoreResult(int correct, int answered, int total) {}
    public record StudentScore(User student, int correct, int total) {}
    public record OptionCount(int index, String label, int count) {}
    public record QuestionPollResult(QuizQuestion question, List<OptionCount> options) {}
}
