package org.specdriven.agent.question;

import org.specdriven.agent.event.EventBus;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * In-memory runtime for a single waiting question per session.
 */
public class QuestionRuntime {

    private final EventBus eventBus;
    private final ConcurrentMap<String, PendingQuestion> pendingBySession = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, QuestionStatus> terminalStatusByQuestionId = new ConcurrentHashMap<>();

    public QuestionRuntime(EventBus eventBus) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    }

    /**
     * Registers a question as waiting for an external answer and emits QUESTION_CREATED.
     */
    public Question beginWaitingQuestion(Question question) {
        Objects.requireNonNull(question, "question");
        if (question.status() != QuestionStatus.WAITING_FOR_ANSWER) {
            throw new IllegalArgumentException("question must be in WAITING_FOR_ANSWER state");
        }

        PendingQuestion pending = new PendingQuestion(question);
        PendingQuestion existing = pendingBySession.putIfAbsent(question.sessionId(), pending);
        if (existing != null) {
            throw new IllegalStateException(
                    "session already has a waiting question: " + existing.question.questionId());
        }

        eventBus.publish(QuestionEvents.questionCreated(question, System.currentTimeMillis()));
        return question;
    }

    /**
     * Submits an answer for the current waiting question of a session.
     */
    public void submitAnswer(String sessionId, String questionId, Answer answer) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(questionId, "questionId");
        Objects.requireNonNull(answer, "answer");

        QuestionStatus terminalStatus = terminalStatusByQuestionId.get(questionId);
        if (terminalStatus != null) {
            throw new IllegalStateException("question is no longer waiting: " + terminalStatus);
        }

        PendingQuestion pending = pendingBySession.get(sessionId);
        if (pending == null) {
            throw new IllegalStateException("no waiting question for session: " + sessionId);
        }
        if (!pending.question.questionId().equals(questionId)) {
            throw new IllegalArgumentException("questionId does not match the waiting question");
        }
        if (pending.question.deliveryMode() != answer.deliveryMode()) {
            throw new IllegalArgumentException("question and answer deliveryMode must match");
        }
        if (!pending.answers.offer(answer)) {
            throw new IllegalStateException("question already has a pending answer");
        }
    }

    /**
     * Polls for a submitted answer for the given waiting question.
     */
    public Optional<Answer> pollAnswer(String sessionId, String questionId, long timeoutMillis)
            throws InterruptedException {
        if (timeoutMillis < 0L) {
            throw new IllegalArgumentException("timeoutMillis must not be negative");
        }

        PendingQuestion pending = requirePendingQuestion(sessionId, questionId);
        return Optional.ofNullable(pending.answers.poll(timeoutMillis, TimeUnit.MILLISECONDS));
    }

    /**
     * Marks a waiting question as answered and emits QUESTION_ANSWERED.
     */
    public Question acceptAnswer(Question waitingQuestion, Answer answer) {
        Objects.requireNonNull(waitingQuestion, "waitingQuestion");
        Objects.requireNonNull(answer, "answer");
        PendingQuestion pending = removePendingQuestion(waitingQuestion.sessionId(), waitingQuestion.questionId());

        if (pending.question.deliveryMode() != answer.deliveryMode()) {
            throw new IllegalArgumentException("question and answer deliveryMode must match");
        }

        Question answeredQuestion = new Question(
                waitingQuestion.questionId(),
                waitingQuestion.sessionId(),
                waitingQuestion.question(),
                waitingQuestion.impact(),
                waitingQuestion.recommendation(),
                QuestionStatus.ANSWERED,
                waitingQuestion.deliveryMode());
        terminalStatusByQuestionId.put(answeredQuestion.questionId(), answeredQuestion.status());
        eventBus.publish(QuestionEvents.questionAnswered(answeredQuestion, answer, System.currentTimeMillis()));
        return answeredQuestion;
    }

    /**
     * Marks a waiting question as expired and emits QUESTION_EXPIRED.
     */
    public Question expireQuestion(Question waitingQuestion) {
        Objects.requireNonNull(waitingQuestion, "waitingQuestion");
        removePendingQuestion(waitingQuestion.sessionId(), waitingQuestion.questionId());

        Question expiredQuestion = new Question(
                waitingQuestion.questionId(),
                waitingQuestion.sessionId(),
                waitingQuestion.question(),
                waitingQuestion.impact(),
                waitingQuestion.recommendation(),
                QuestionStatus.EXPIRED,
                waitingQuestion.deliveryMode());
        terminalStatusByQuestionId.put(expiredQuestion.questionId(), expiredQuestion.status());
        eventBus.publish(QuestionEvents.questionExpired(expiredQuestion, System.currentTimeMillis()));
        return expiredQuestion;
    }

    /**
     * Closes the waiting question without emitting an additional lifecycle event.
     */
    public Question closeQuestion(Question waitingQuestion) {
        Objects.requireNonNull(waitingQuestion, "waitingQuestion");
        removePendingQuestion(waitingQuestion.sessionId(), waitingQuestion.questionId());

        Question closedQuestion = new Question(
                waitingQuestion.questionId(),
                waitingQuestion.sessionId(),
                waitingQuestion.question(),
                waitingQuestion.impact(),
                waitingQuestion.recommendation(),
                QuestionStatus.CLOSED,
                waitingQuestion.deliveryMode());
        terminalStatusByQuestionId.put(closedQuestion.questionId(), closedQuestion.status());
        return closedQuestion;
    }

    public Optional<Question> pendingQuestion(String sessionId) {
        PendingQuestion pending = pendingBySession.get(sessionId);
        return pending == null ? Optional.empty() : Optional.of(pending.question);
    }

    private PendingQuestion requirePendingQuestion(String sessionId, String questionId) {
        PendingQuestion pending = pendingBySession.get(sessionId);
        if (pending == null || !pending.question.questionId().equals(questionId)) {
            throw new IllegalStateException("no waiting question found for session/question");
        }
        return pending;
    }

    private PendingQuestion removePendingQuestion(String sessionId, String questionId) {
        PendingQuestion pending = requirePendingQuestion(sessionId, questionId);
        if (!pendingBySession.remove(sessionId, pending)) {
            throw new IllegalStateException("waiting question changed while being completed");
        }
        return pending;
    }

    private static final class PendingQuestion {
        private final Question question;
        private final ArrayBlockingQueue<Answer> answers = new ArrayBlockingQueue<>(1);

        private PendingQuestion(Question question) {
            this.question = question;
        }
    }
}
