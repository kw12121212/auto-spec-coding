package org.specdriven.agent.question;

/**
 * Captures a single delivery attempt to an external channel.
 *
 * @param questionId   the question being delivered
 * @param channelType  the channel type (e.g. "telegram", "discord")
 * @param attemptNumber 1-based attempt counter
 * @param status       outcome of this attempt
 * @param statusCode   HTTP or protocol status code, or null
 * @param errorMessage error description, or null
 * @param attemptedAt  epoch millis when the attempt occurred
 */
public record DeliveryAttempt(
        String questionId,
        String channelType,
        int attemptNumber,
        DeliveryStatus status,
        Integer statusCode,
        String errorMessage,
        long attemptedAt
) {
    public DeliveryAttempt {
        if (questionId == null || questionId.isBlank()) {
            throw new IllegalArgumentException("questionId must not be blank");
        }
        if (channelType == null || channelType.isBlank()) {
            throw new IllegalArgumentException("channelType must not be blank");
        }
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be >= 1");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
    }
}
