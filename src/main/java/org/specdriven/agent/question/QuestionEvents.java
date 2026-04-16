package org.specdriven.agent.question;

import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Factory for standard question lifecycle events and metadata.
 */
public final class QuestionEvents {

    private QuestionEvents() {
    }

    public static Event questionCreated(Question question, long timestamp) {
        Objects.requireNonNull(question, "question");
        return new Event(EventType.QUESTION_CREATED, timestamp, question.sessionId(), question.toPayload());
    }

    public static Event questionAnswered(Question question, Answer answer, long timestamp) {
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(answer, "answer");
        validateDeliveryMode(question, answer);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("questionId", question.questionId());
        metadata.put("sessionId", question.sessionId());
        metadata.putAll(QuestionRoutingPolicy.routingMetadata(question.category(), question.deliveryMode()));
        metadata.put("status", question.status().name());
        metadata.put("content", answer.content());
        metadata.putAll(answer.toAuditMetadata());
        return new Event(EventType.QUESTION_ANSWERED, timestamp, question.sessionId(), metadata);
    }

    public static Event questionEscalated(Question question, Answer answer, long timestamp) {
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(answer, "answer");
        validateDeliveryMode(question, answer);
        if (answer.escalationReason() == null) {
            throw new IllegalArgumentException("escalationReason must be present for escalated questions");
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("questionId", question.questionId());
        metadata.put("sessionId", question.sessionId());
        metadata.putAll(QuestionRoutingPolicy.routingMetadata(question.category(), question.deliveryMode()));
        metadata.put("escalationReason", answer.escalationReason());
        metadata.put("decision", answer.decision().name());
        metadata.put("status", question.status().name());
        return new Event(EventType.QUESTION_ESCALATED, timestamp, question.sessionId(), metadata);
    }

    public static Event questionExpired(Question question, long timestamp) {
        Objects.requireNonNull(question, "question");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("questionId", question.questionId());
        metadata.put("sessionId", question.sessionId());
        metadata.putAll(QuestionRoutingPolicy.routingMetadata(question.category(), question.deliveryMode()));
        metadata.put("status", QuestionStatus.EXPIRED.name());
        return new Event(EventType.QUESTION_EXPIRED, timestamp, question.sessionId(), metadata);
    }

    private static void validateDeliveryMode(Question question, Answer answer) {
        if (question.deliveryMode() != answer.deliveryMode()) {
            throw new IllegalArgumentException("question and answer deliveryMode must match");
        }
    }
}
