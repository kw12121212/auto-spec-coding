package org.specdriven.agent.question;

import java.util.List;
import java.util.Optional;

/**
 * Persistent storage for {@link Question} instances.
 */
public interface QuestionStore {

    /**
     * Persists a question and returns its questionId.
     */
    String save(Question question);

    /**
     * Updates the status of a question and returns the updated instance.
     */
    Question update(String questionId, QuestionStatus status);

    /**
     * Returns all questions for the given session.
     */
    List<Question> findBySession(String sessionId);

    /**
     * Returns all questions matching the given status.
     */
    List<Question> findByStatus(QuestionStatus status);

    /**
     * Returns the question in WAITING_FOR_ANSWER for the given session, if any.
     */
    Optional<Question> findPending(String sessionId);

    /**
     * Removes a question from the store.
     */
    void delete(String questionId);
}
