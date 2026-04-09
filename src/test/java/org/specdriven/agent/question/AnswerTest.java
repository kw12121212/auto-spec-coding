package org.specdriven.agent.question;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnswerTest {

    @Test
    void construction_withAiAnswer() {
        Answer answer = new Answer(
                "Use branch release/1.0.",
                "The release branch matches the target environment.",
                "answer-agent:run-7",
                AnswerSource.AI_AGENT,
                0.82d,
                QuestionDecision.ANSWER_ACCEPTED,
                DeliveryMode.AUTO_AI_REPLY,
                null,
                1234L
        );

        assertEquals("Use branch release/1.0.", answer.content());
        assertEquals("The release branch matches the target environment.", answer.basisSummary());
        assertNull(answer.escalationReason());
    }

    @Test
    void toAuditMetadata_containsSerializedEnumsAndAnswerFields() {
        Answer answer = new Answer(
                "Wait for human approval.",
                "This operation mutates production data.",
                "operator:alice",
                AnswerSource.HUMAN_INLINE,
                1.0d,
                QuestionDecision.ESCALATE_TO_HUMAN,
                DeliveryMode.PAUSE_WAIT_HUMAN,
                "Production write requires explicit approval.",
                4567L
        );

        Map<String, Object> metadata = answer.toAuditMetadata();

        assertEquals("HUMAN_INLINE", metadata.get("source"));
        assertEquals("ESCALATE_TO_HUMAN", metadata.get("decision"));
        assertEquals("PAUSE_WAIT_HUMAN", metadata.get("deliveryMode"));
        assertEquals("Production write requires explicit approval.", metadata.get("escalationReason"));
        assertThrows(UnsupportedOperationException.class, () -> metadata.put("extra", "x"));
    }

    @Test
    void confidenceOutsideRange_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new Answer(
                "a", "basis", "source", AnswerSource.AI_AGENT, -0.1d,
                QuestionDecision.ANSWER_ACCEPTED, DeliveryMode.AUTO_AI_REPLY, null, 1L));
        assertThrows(IllegalArgumentException.class, () -> new Answer(
                "a", "basis", "source", AnswerSource.AI_AGENT, 1.1d,
                QuestionDecision.ANSWER_ACCEPTED, DeliveryMode.AUTO_AI_REPLY, null, 1L));
    }

    @Test
    void escalatedAnswer_requiresEscalationReason() {
        assertThrows(IllegalArgumentException.class, () -> new Answer(
                "Need human review.",
                "Sensitive permission request.",
                "answer-agent:run-1",
                AnswerSource.AI_AGENT,
                0.6d,
                QuestionDecision.ESCALATE_TO_HUMAN,
                DeliveryMode.AUTO_AI_REPLY,
                null,
                10L
        ));
    }

    @Test
    void humanRoutedAnswer_requiresEscalationReason() {
        assertThrows(IllegalArgumentException.class, () -> new Answer(
                "Waiting for mobile response.",
                "The user must approve deployment.",
                "user:mobile",
                AnswerSource.HUMAN_MOBILE,
                1.0d,
                QuestionDecision.ANSWER_ACCEPTED,
                DeliveryMode.PUSH_MOBILE_WAIT_HUMAN,
                "",
                20L
        ));
    }
}
