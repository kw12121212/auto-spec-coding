package org.specdriven.agent.question;

import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;

import java.util.Map;

/**
 * Decorator that wraps a {@link QuestionDeliveryChannel} with retry logic.
 * Retries on {@link MobileAdapterException} up to the configured max attempts
 * with exponential backoff. Logs each attempt to a {@link DeliveryLogStore}
 * and emits delivery lifecycle events via {@link EventBus}.
 */
public class RetryingDeliveryChannel implements QuestionDeliveryChannel {

    private final QuestionDeliveryChannel delegate;
    private final String channelType;
    private final RetryConfig retryConfig;
    private final DeliveryLogStore logStore;
    private final EventBus eventBus;

    public RetryingDeliveryChannel(QuestionDeliveryChannel delegate,
                                   String channelType,
                                   RetryConfig retryConfig,
                                   DeliveryLogStore logStore,
                                   EventBus eventBus) {
        this.delegate = delegate;
        this.channelType = channelType;
        this.retryConfig = retryConfig;
        this.logStore = logStore;
        this.eventBus = eventBus;
    }

    @Override
    public void send(Question question) {
        int attempts = retryConfig.maxAttempts();

        for (int attempt = 1; attempt <= attempts; attempt++) {
            long now = System.currentTimeMillis();
            emitEvent(EventType.DELIVERY_ATTEMPTED, question, attempt, null);

            try {
                delegate.send(question);

                DeliveryAttempt success = new DeliveryAttempt(
                        question.questionId(), channelType, attempt,
                        DeliveryStatus.SENT, null, null, now);
                logStore.save(success);
                emitEvent(EventType.DELIVERY_SUCCEEDED, question, attempt, null);
                return;
            } catch (MobileAdapterException e) {
                boolean isLastAttempt = (attempt == attempts);
                DeliveryStatus status = isLastAttempt ? DeliveryStatus.FAILED : DeliveryStatus.RETRYING;

                DeliveryAttempt failAttempt = new DeliveryAttempt(
                        question.questionId(), channelType, attempt,
                        status, null, e.getMessage(), now);
                logStore.save(failAttempt);

                if (isLastAttempt) {
                    emitEvent(EventType.DELIVERY_FAILED, question, attempt, e.getMessage());
                    throw e;
                }

                sleep(calculateDelay(attempt));
            }
        }
    }

    @Override
    public void close() {
        delegate.close();
    }

    /** Returns the underlying delegate channel. */
    public QuestionDeliveryChannel delegate() {
        return delegate;
    }

    private long calculateDelay(int attempt) {
        // attempt 1 failed → delay before attempt 2 = initialDelay * multiplier^0
        // attempt 2 failed → delay before attempt 3 = initialDelay * multiplier^1
        int exponent = attempt - 1;
        double delay = retryConfig.initialDelayMs() * Math.pow(retryConfig.backoffMultiplier(), exponent);
        return (long) delay;
    }

    private void sleep(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Delivery retry interrupted", e);
        }
    }

    private void emitEvent(EventType type, Question question, int attemptNumber, String errorMessage) {
        try {
            Map<String, Object> metadata = new java.util.LinkedHashMap<>();
            metadata.put("questionId", question.questionId());
            metadata.put("channelType", channelType);
            metadata.put("attemptNumber", attemptNumber);
            if (errorMessage != null) {
                metadata.put("errorMessage", errorMessage);
            }
            eventBus.publish(new Event(type, System.currentTimeMillis(),
                    question.sessionId(), metadata));
        } catch (Exception e) {
            // Event emission failure must not break delivery
        }
    }
}
