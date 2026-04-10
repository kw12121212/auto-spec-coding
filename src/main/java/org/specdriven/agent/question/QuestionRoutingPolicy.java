package org.specdriven.agent.question;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Default routing rules for structured questions.
 */
public final class QuestionRoutingPolicy {

    private QuestionRoutingPolicy() {
    }

    public static DeliveryMode defaultDeliveryMode(QuestionCategory category) {
        return switch (Objects.requireNonNull(category, "category")) {
            case CLARIFICATION, PLAN_SELECTION -> DeliveryMode.AUTO_AI_REPLY;
            case PERMISSION_CONFIRMATION, IRREVERSIBLE_APPROVAL -> DeliveryMode.PAUSE_WAIT_HUMAN;
        };
    }

    public static boolean allowsAutoAiReply(QuestionCategory category) {
        return defaultDeliveryMode(category) == DeliveryMode.AUTO_AI_REPLY;
    }

    public static void validate(QuestionCategory category, DeliveryMode deliveryMode) {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(deliveryMode, "deliveryMode");
        if (!allowsAutoAiReply(category) && deliveryMode == DeliveryMode.AUTO_AI_REPLY) {
            throw new IllegalArgumentException(category + " requires human approval and cannot use AUTO_AI_REPLY");
        }
    }

    public static Map<String, Object> routingMetadata(QuestionCategory category, DeliveryMode deliveryMode) {
        validate(category, deliveryMode);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("category", category.name());
        metadata.put("deliveryMode", deliveryMode.name());
        metadata.put("routingReason", routingReason(category));
        return metadata;
    }

    public static String routingReason(QuestionCategory category) {
        return switch (Objects.requireNonNull(category, "category")) {
            case CLARIFICATION -> "Category CLARIFICATION may use AUTO_AI_REPLY by default.";
            case PLAN_SELECTION -> "Category PLAN_SELECTION may use AUTO_AI_REPLY by default.";
            case PERMISSION_CONFIRMATION -> "Category PERMISSION_CONFIRMATION requires human approval.";
            case IRREVERSIBLE_APPROVAL -> "Category IRREVERSIBLE_APPROVAL requires human approval.";
        };
    }
}
