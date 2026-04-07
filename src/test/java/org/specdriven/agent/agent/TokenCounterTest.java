package org.specdriven.agent.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TokenCounterTest {

    @Test
    void estimateNonEmptyText() {
        int count = TokenCounter.estimate("Hello, world!");
        assertTrue(count > 0);
    }

    @Test
    void estimateEmptyString() {
        assertEquals(0, TokenCounter.estimate(""));
    }

    @Test
    void estimateNull() {
        assertEquals(0, TokenCounter.estimate((String) null));
    }

    @Test
    void estimateMessages() {
        List<Message> messages = List.of(
                new UserMessage("Hello", 0),
                new AssistantMessage("Hi there!", 0)
        );
        int count = TokenCounter.estimate(messages);
        assertTrue(count > 0);
    }

    @Test
    void estimateEmptyMessageList() {
        assertEquals(0, TokenCounter.estimate(List.of()));
    }

    @Test
    void estimateNullMessageList() {
        assertEquals(0, TokenCounter.estimate((List<Message>) null));
    }

    @Test
    void estimateIsConsistentWithItself() {
        String text = "The quick brown fox jumps over the lazy dog.";
        int count1 = TokenCounter.estimate(text);
        int count2 = TokenCounter.estimate(text);
        assertEquals(count1, count2);
    }
}
