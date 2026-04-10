package org.specdriven.agent.question;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionRoutingPolicyTest {

    @Test
    void defaultDeliveryMode_matchesEachCategory() {
        assertEquals(DeliveryMode.AUTO_AI_REPLY,
                QuestionRoutingPolicy.defaultDeliveryMode(QuestionCategory.CLARIFICATION));
        assertEquals(DeliveryMode.AUTO_AI_REPLY,
                QuestionRoutingPolicy.defaultDeliveryMode(QuestionCategory.PLAN_SELECTION));
        assertEquals(DeliveryMode.PAUSE_WAIT_HUMAN,
                QuestionRoutingPolicy.defaultDeliveryMode(QuestionCategory.PERMISSION_CONFIRMATION));
        assertEquals(DeliveryMode.PAUSE_WAIT_HUMAN,
                QuestionRoutingPolicy.defaultDeliveryMode(QuestionCategory.IRREVERSIBLE_APPROVAL));
    }

    @Test
    void allowsAutoAiReply_onlyForLowRiskCategories() {
        assertTrue(QuestionRoutingPolicy.allowsAutoAiReply(QuestionCategory.CLARIFICATION));
        assertTrue(QuestionRoutingPolicy.allowsAutoAiReply(QuestionCategory.PLAN_SELECTION));
        assertFalse(QuestionRoutingPolicy.allowsAutoAiReply(QuestionCategory.PERMISSION_CONFIRMATION));
        assertFalse(QuestionRoutingPolicy.allowsAutoAiReply(QuestionCategory.IRREVERSIBLE_APPROVAL));
    }

    @Test
    void routingMetadata_explainsHumanOnlyRoutes() {
        Map<String, Object> metadata = QuestionRoutingPolicy.routingMetadata(
                QuestionCategory.IRREVERSIBLE_APPROVAL,
                DeliveryMode.PAUSE_WAIT_HUMAN
        );

        assertEquals("IRREVERSIBLE_APPROVAL", metadata.get("category"));
        assertEquals("PAUSE_WAIT_HUMAN", metadata.get("deliveryMode"));
        assertEquals("Category IRREVERSIBLE_APPROVAL requires human approval.", metadata.get("routingReason"));
    }

    @Test
    void humanOnlyCategory_rejectsAutoAiReply() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> QuestionRoutingPolicy.validate(
                        QuestionCategory.PERMISSION_CONFIRMATION,
                        DeliveryMode.AUTO_AI_REPLY));

        assertTrue(error.getMessage().contains("requires human approval"));
    }
}
