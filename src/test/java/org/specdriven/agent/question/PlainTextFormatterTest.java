package org.specdriven.agent.question;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlainTextFormatterTest {

    private static Question sampleQuestion() {
        return new Question(
                "q-1", "s-1",
                "Which approach?", "Wrong choice delays delivery",
                "Use option A",
                QuestionStatus.WAITING_FOR_ANSWER,
                QuestionCategory.PLAN_SELECTION,
                DeliveryMode.PUSH_MOBILE_WAIT_HUMAN
        );
    }

    @Test
    void formatContainsAllRequiredFields() {
        String result = PlainTextFormatter.INSTANCE.format(sampleQuestion());
        assertTrue(result.contains("[Question] Which approach?"));
        assertTrue(result.contains("[Impact] Wrong choice delays delivery"));
        assertTrue(result.contains("[Recommendation] Use option A"));
        assertTrue(result.contains("[Session] s-1"));
        assertTrue(result.contains("[Question ID] q-1"));
    }

    @Test
    void formatIsNonEmpty() {
        String result = PlainTextFormatter.INSTANCE.format(sampleQuestion());
        assertFalse(result.isEmpty());
    }

    @Test
    void singletonInstance() {
        assertSame(PlainTextFormatter.INSTANCE, PlainTextFormatter.INSTANCE);
    }
}
