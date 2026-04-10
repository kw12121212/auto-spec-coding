package org.specdriven.agent.question;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Facade that combines delivery channel, reply collection, and question storage.
 */
public class QuestionDeliveryService {

    private final QuestionDeliveryChannel channel;
    private final QuestionReplyCollector collector;
    private final QuestionRuntime questionRuntime;
    private final QuestionStore questionStore;

    public QuestionDeliveryService(QuestionDeliveryChannel channel,
                                   QuestionReplyCollector collector,
                                   QuestionRuntime questionRuntime,
                                   QuestionStore questionStore) {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.collector = Objects.requireNonNull(collector, "collector");
        this.questionRuntime = Objects.requireNonNull(questionRuntime, "questionRuntime");
        this.questionStore = Objects.requireNonNull(questionStore, "questionStore");
    }

    /**
     * Delivers a question to the configured channel and persists it.
     */
    public void deliver(Question question) {
        questionStore.save(question);
        channel.send(question);
    }

    /**
     * Submits a human reply for a waiting question.
     * Validates, collects, and persists the state change.
     */
    public void submitReply(String sessionId, String questionId, Answer answer) {
        collector.collect(sessionId, questionId, answer);
        questionStore.update(questionId, QuestionStatus.ANSWERED);
    }

    /**
     * Returns pending (WAITING_FOR_ANSWER) questions for a session.
     */
    public Optional<Question> pendingQuestion(String sessionId) {
        return questionRuntime.pendingQuestion(sessionId);
    }

    /**
     * Returns all questions for a session from the store.
     */
    public List<Question> questionsBySession(String sessionId) {
        return questionStore.findBySession(sessionId);
    }

    public QuestionDeliveryChannel channel() {
        return channel;
    }

    public QuestionReplyCollector collector() {
        return collector;
    }

    public QuestionRuntime runtime() {
        return questionRuntime;
    }

    public QuestionStore store() {
        return questionStore;
    }
}
