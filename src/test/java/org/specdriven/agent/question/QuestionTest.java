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
                QuestionCategory.PLAN_SELECTION,
                DeliveryMode.AUTO_AI_REPLY
        );

        assertEquals("q-1", question.questionId());
        assertEquals("session-1", question.sessionId());
        assertEquals("Which branch should I take?", question.question());
        assertEquals(QuestionStatus.OPEN, question.status());
        assertEquals(QuestionCategory.PLAN_SELECTION, question.category());
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
                QuestionCategory.IRREVERSIBLE_APPROVAL,
                DeliveryMode.PAUSE_WAIT_HUMAN
        );

        Map<String, Object> payload = question.toPayload();

        assertEquals("Need approval?", payload.get("question"));
        assertEquals("Potential irreversible operation.", payload.get("impact"));
        assertEquals("Ask a human before proceeding.", payload.get("recommendation"));
        assertEquals("WAITING_FOR_ANSWER", payload.get("status"));
        assertEquals("IRREVERSIBLE_APPROVAL", payload.get("category"));
        assertEquals("PAUSE_WAIT_HUMAN", payload.get("deliveryMode"));
        assertEquals("Category IRREVERSIBLE_APPROVAL requires human approval.", payload.get("routingReason"));
        assertThrows(UnsupportedOperationException.class, () -> payload.put("extra", "x"));
    }

    @Test
    void humanOnlyCategory_rejectsAutoAiReply() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new Question(
                "q-3",
                "session-3",
                "Should we delete the backup?",
                "Deletion is irreversible.",
                "Require explicit approval.",
                QuestionStatus.OPEN,
                QuestionCategory.IRREVERSIBLE_APPROVAL,
                DeliveryMode.AUTO_AI_REPLY
        ));

        assertTrue(error.getMessage().contains("requires human approval"));
    }

    @Test
    void blankRequiredFields_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new Question(
                " ", "session", "question", "impact", "recommendation",
                QuestionStatus.OPEN, QuestionCategory.CLARIFICATION, DeliveryMode.AUTO_AI_REPLY));
        assertThrows(IllegalArgumentException.class, () -> new Question(
                "q", "", "question", "impact", "recommendation",
                QuestionStatus.OPEN, QuestionCategory.CLARIFICATION, DeliveryMode.AUTO_AI_REPLY));
        assertThrows(IllegalArgumentException.class, () -> new Question(
                "q", "session", " ", "impact", "recommendation",
                QuestionStatus.OPEN, QuestionCategory.CLARIFICATION, DeliveryMode.AUTO_AI_REPLY));
    }
}
