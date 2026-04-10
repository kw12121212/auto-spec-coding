package org.specdriven.agent.question;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventType;

import static org.junit.jupiter.api.Assertions.*;

class QuestionEventsTest {

    @Test
    void questionCreated_containsRequiredMetadata() {
        Question question = new Question(
                "q-10",
                "session-10",
                "What timeout should be used?",
                "A wrong timeout may break the rollout.",
                "Use the existing production default.",
                QuestionStatus.OPEN,
                QuestionCategory.CLARIFICATION,
                DeliveryMode.AUTO_AI_REPLY
        );

        Event event = QuestionEvents.questionCreated(question, 100L);

        assertEquals(EventType.QUESTION_CREATED, event.type());
        assertEquals("session-10", event.source());
        assertEquals("q-10", event.metadata().get("questionId"));
        assertEquals("session-10", event.metadata().get("sessionId"));
        assertEquals("CLARIFICATION", event.metadata().get("category"));
        assertEquals("AUTO_AI_REPLY", event.metadata().get("deliveryMode"));
        assertEquals("OPEN", event.metadata().get("status"));
        assertTrue(event.toJson().contains("\"deliveryMode\":\"AUTO_AI_REPLY\""));
    }

    @Test
    void questionAnswered_containsAuditMetadata() {
        Question question = new Question(
                "q-11",
                "session-11",
                "Should we retry?",
                "Skipping retry may leave the task incomplete.",
                "Retry once with the known safe parameters.",
                QuestionStatus.ANSWERED,
                QuestionCategory.PLAN_SELECTION,
                DeliveryMode.AUTO_AI_REPLY
        );
        Answer answer = new Answer(
                "Retry once.",
                "The previous failure was transient and idempotent.",
                "answer-agent:retry-check",
                AnswerSource.AI_AGENT,
                0.74d,
                QuestionDecision.ANSWER_ACCEPTED,
                DeliveryMode.AUTO_AI_REPLY,
                null,
                200L
        );

        Event event = QuestionEvents.questionAnswered(question, answer, 210L);

        assertEquals(EventType.QUESTION_ANSWERED, event.type());
        assertEquals("q-11", event.metadata().get("questionId"));
        assertEquals("session-11", event.metadata().get("sessionId"));
        assertEquals("PLAN_SELECTION", event.metadata().get("category"));
        assertEquals("ANSWER_ACCEPTED", event.metadata().get("decision"));
        assertEquals("AI_AGENT", event.metadata().get("source"));
        assertEquals(0.74d, (Double) event.metadata().get("confidence"));
        assertEquals("The previous failure was transient and idempotent.", event.metadata().get("basisSummary"));
        assertEquals("answer-agent:retry-check", event.metadata().get("sourceRef"));
    }

    @Test
    void questionEscalated_containsEscalationReason() {
        Question question = new Question(
                "q-12",
                "session-12",
                "Deploy now?",
                "Immediate deploy may break production.",
                "Escalate to a human approver.",
                QuestionStatus.ESCALATED,
                QuestionCategory.IRREVERSIBLE_APPROVAL,
                DeliveryMode.PAUSE_WAIT_HUMAN
        );
        Answer answer = new Answer(
                "Escalate to operator.",
                "This is an irreversible production change.",
                "operator:bob",
                AnswerSource.HUMAN_INLINE,
                1.0d,
                QuestionDecision.ESCALATE_TO_HUMAN,
                DeliveryMode.PAUSE_WAIT_HUMAN,
                "Irreversible production changes require human approval.",
                300L
        );

        Event event = QuestionEvents.questionEscalated(question, answer, 320L);

        assertEquals(EventType.QUESTION_ESCALATED, event.type());
        assertEquals("IRREVERSIBLE_APPROVAL", event.metadata().get("category"));
        assertEquals("Irreversible production changes require human approval.",
                event.metadata().get("escalationReason"));
        assertEquals("Category IRREVERSIBLE_APPROVAL requires human approval.", event.metadata().get("routingReason"));
        assertEquals("PAUSE_WAIT_HUMAN", event.metadata().get("deliveryMode"));
    }

    @Test
    void mismatchedDeliveryModes_rejected() {
        Question question = new Question(
                "q-13",
                "session-13",
                "Need mobile approval?",
                "Deploying without approval is unsafe.",
                "Send to mobile approver.",
                QuestionStatus.WAITING_FOR_ANSWER,
                QuestionCategory.PERMISSION_CONFIRMATION,
                DeliveryMode.PUSH_MOBILE_WAIT_HUMAN
        );
        Answer answer = new Answer(
                "Wait for mobile approval.",
                "Manual gate is required.",
                "operator:carol",
                AnswerSource.HUMAN_MOBILE,
                1.0d,
                QuestionDecision.ANSWER_ACCEPTED,
                DeliveryMode.PAUSE_WAIT_HUMAN,
                "Human approval is mandatory.",
                400L
        );

        assertThrows(IllegalArgumentException.class, () -> QuestionEvents.questionAnswered(question, answer, 450L));
    }
}
