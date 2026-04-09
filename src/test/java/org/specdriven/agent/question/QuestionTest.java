package org.specdriven.agent.question;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QuestionTest {

    @Test
    void construction_withRequiredFields() {
        Question question = new Question(
                "q-1",
                "session-1",
                "Which branch should I take?",
                "Choosing the wrong branch will delay delivery.",
                "Prefer the stable release branch.",
                QuestionStatus.OPEN,
                DeliveryMode.AUTO_AI_REPLY
        );

        assertEquals("q-1", question.questionId());
        assertEquals("session-1", question.sessionId());
        assertEquals("Which branch should I take?", question.question());
        assertEquals(QuestionStatus.OPEN, question.status());
        assertEquals(DeliveryMode.AUTO_AI_REPLY, question.deliveryMode());
    }

    @Test
    void toPayload_containsRequiredFieldsAndSerializedEnums() {
        Question question = new Question(
                "q-2",
                "session-2",
                "Need approval?",
                "Potential irreversible operation.",
                "Ask a human before proceeding.",
                QuestionStatus.WAITING_FOR_ANSWER,
                DeliveryMode.PAUSE_WAIT_HUMAN
        );

        Map<String, Object> payload = question.toPayload();

        assertEquals("Need approval?", payload.get("question"));
        assertEquals("Potential irreversible operation.", payload.get("impact"));
        assertEquals("Ask a human before proceeding.", payload.get("recommendation"));
        assertEquals("WAITING_FOR_ANSWER", payload.get("status"));
        assertEquals("PAUSE_WAIT_HUMAN", payload.get("deliveryMode"));
        assertThrows(UnsupportedOperationException.class, () -> payload.put("extra", "x"));
    }

    @Test
    void blankRequiredFields_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new Question(
                " ", "session", "question", "impact", "recommendation",
                QuestionStatus.OPEN, DeliveryMode.AUTO_AI_REPLY));
        assertThrows(IllegalArgumentException.class, () -> new Question(
                "q", "", "question", "impact", "recommendation",
                QuestionStatus.OPEN, DeliveryMode.AUTO_AI_REPLY));
        assertThrows(IllegalArgumentException.class, () -> new Question(
                "q", "session", " ", "impact", "recommendation",
                QuestionStatus.OPEN, DeliveryMode.AUTO_AI_REPLY));
    }
}
