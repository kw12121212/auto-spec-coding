package org.specdriven.agent.question;

import java.util.List;
import java.util.Optional;

/**
 * Persistent storage for delivery attempt records.
 */
public interface DeliveryLogStore {

    /**
     * Persists a delivery attempt.
     */
    void save(DeliveryAttempt attempt);

    /**
     * Returns all delivery attempts for the given question, ordered by attemptNumber.
     */
    List<DeliveryAttempt> findByQuestion(String questionId);

    /**
     * Returns the latest delivery attempt for the given question, or empty if none.
     */
    Optional<DeliveryAttempt> findLatestByQuestion(String questionId);
}
