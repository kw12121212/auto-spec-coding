package org.specdriven.agent.question;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiscordMessageTemplateTest {

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
    void channelTypeIsDiscord() {
        assertEquals("discord", new DiscordMessageTemplate().channelType());
    }

    @Test
    void formatContainsMarkdownBoldLabels() {
        String result = new DiscordMessageTemplate().format(sampleQuestion());
        assertTrue(result.contains("**Question:**"));
        assertTrue(result.contains("**Impact:**"));
        assertTrue(result.contains("**Recommendation:**"));
        assertTrue(result.contains("**Session:**"));
        assertTrue(result.contains("**Question ID:**"));
    }

    @Test
    void formatContainsFieldValues() {
        String result = new DiscordMessageTemplate().format(sampleQuestion());
        assertTrue(result.contains("Which approach?"));
        assertTrue(result.contains("Wrong choice delays delivery"));
        assertTrue(result.contains("Use option A"));
        assertTrue(result.contains("s-1"));
        assertTrue(result.contains("q-1"));
    }

    @Test
    void formatImplementsRichMessageFormatter() {
        RichMessageFormatter formatter = new DiscordMessageTemplate();
        String result = formatter.format(sampleQuestion());
        assertFalse(result.isEmpty());
    }
}
