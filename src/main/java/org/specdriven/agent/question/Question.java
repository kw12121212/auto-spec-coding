package org.specdriven.agent.question;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Structured question raised during agent execution.
 *
 * @param questionId     stable question identifier
 * @param sessionId      originating agent session identifier
 * @param question       human-readable question text
 * @param impact         impact of not answering or choosing incorrectly
 * @param recommendation suggested resolution or preferred answer
 * @param status         observable lifecycle state
 * @param deliveryMode   configured answer path
 */
public record Question(
        String questionId,
        String sessionId,
        String question,
        String impact,
        String recommendation,
        QuestionStatus status,
        DeliveryMode deliveryMode
) {
    public Question {
        questionId = requireNonBlank(questionId, "questionId");
        sessionId = requireNonBlank(sessionId, "sessionId");
        question = requireNonBlank(question, "question");
        impact = requireNonBlank(impact, "impact");
        recommendation = requireNonBlank(recommendation, "recommendation");
        status = Objects.requireNonNull(status, "status");
        deliveryMode = Objects.requireNonNull(deliveryMode, "deliveryMode");
    }

    /**
     * Returns the canonical structured payload for this question.
     */
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("questionId", questionId);
        payload.put("sessionId", sessionId);
        payload.put("question", question);
        payload.put("impact", impact);
        payload.put("recommendation", recommendation);
        payload.put("status", status.name());
        payload.put("deliveryMode", deliveryMode.name());
        return Collections.unmodifiableMap(payload);
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
