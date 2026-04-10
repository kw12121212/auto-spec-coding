package org.specdriven.agent.question;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Default {@link QuestionReplyCollector} that stores answers in an in-memory queue
 * and forwards them to a {@link QuestionRuntime}.
 */
public class InMemoryReplyCollector implements QuestionReplyCollector {

    private final QuestionRuntime questionRuntime;
    private final ConcurrentMap<String, BlockingQueue<Answer>> answersBySession = new ConcurrentHashMap<>();

    public InMemoryReplyCollector(QuestionRuntime questionRuntime) {
        this.questionRuntime = Objects.requireNonNull(questionRuntime, "questionRuntime");
    }

    @Override
    public void collect(String sessionId, String questionId, Answer answer) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(questionId, "questionId");
        Objects.requireNonNull(answer, "answer");

        // Validate against the runtime's in-memory state
        Question pending = questionRuntime.pendingQuestion(sessionId)
                .orElseThrow(() -> new IllegalStateException(
                        "no waiting question for session: " + sessionId));

        if (!pending.questionId().equals(questionId)) {
            throw new IllegalArgumentException("questionId does not match the waiting question");
        }
        if (pending.deliveryMode() != answer.deliveryMode()) {
            throw new IllegalArgumentException("question and answer deliveryMode must match");
        }

        // Submit to the runtime's answer queue
        questionRuntime.submitAnswer(sessionId, questionId, answer);
    }

    @Override
    public void close() {
        answersBySession.clear();
    }
}
