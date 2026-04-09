package org.specdriven.agent.question;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Structured answer for a question.
 *
 * @param content          answer content returned to the question issuer
 * @param basisSummary     short explanation of the basis used for the answer
 * @param sourceRef        reference to the source or actor that produced the answer
 * @param source           answer origin
 * @param confidence       confidence in the inclusive range [0.0, 1.0]
 * @param decision         final handling decision for the question
 * @param deliveryMode     delivery mode under which the answer was resolved
 * @param escalationReason non-empty when the question was escalated or routed for human handling
 * @param answeredAt       epoch millis when the answer was produced
 */
public record Answer(
        String content,
        String basisSummary,
        String sourceRef,
        AnswerSource source,
        double confidence,
        QuestionDecision decision,
        DeliveryMode deliveryMode,
        String escalationReason,
        long answeredAt
) {
    public Answer {
        content = requireNonBlank(content, "content");
        basisSummary = requireNonBlank(basisSummary, "basisSummary");
        sourceRef = requireNonBlank(sourceRef, "sourceRef");
        source = Objects.requireNonNull(source, "source");
        decision = Objects.requireNonNull(decision, "decision");
        deliveryMode = Objects.requireNonNull(deliveryMode, "deliveryMode");
        if (confidence < 0.0d || confidence > 1.0d) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        if (answeredAt < 0L) {
            throw new IllegalArgumentException("answeredAt must not be negative");
        }
        if (requiresEscalationReason(decision, deliveryMode)) {
            escalationReason = requireNonBlank(escalationReason, "escalationReason");
        } else if (escalationReason != null && escalationReason.isBlank()) {
            escalationReason = null;
        }
    }

    /**
     * Returns the canonical audit metadata for this answer.
     */
    public Map<String, Object> toAuditMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", source.name());
        metadata.put("basisSummary", basisSummary);
        metadata.put("confidence", confidence);
        metadata.put("sourceRef", sourceRef);
        metadata.put("decision", decision.name());
        metadata.put("deliveryMode", deliveryMode.name());
        metadata.put("answeredAt", answeredAt);
        if (escalationReason != null) {
            metadata.put("escalationReason", escalationReason);
        }
        return Collections.unmodifiableMap(metadata);
    }

    private static boolean requiresEscalationReason(QuestionDecision decision, DeliveryMode deliveryMode) {
        return decision == QuestionDecision.ESCALATE_TO_HUMAN || deliveryMode != DeliveryMode.AUTO_AI_REPLY;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
