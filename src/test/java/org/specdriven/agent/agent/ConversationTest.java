package org.specdriven.agent.agent;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConversationTest {

    private Conversation conv;

    @BeforeEach
    void setUp() {
        conv = new Conversation();
    }

    @Test
    void appendAndGet() {
        UserMessage msg = new UserMessage("hello", 1000L);
        conv.append(msg);
        assertEquals(1, conv.size());
        assertEquals(msg, conv.get(0));
    }

    @Test
    void multipleMessages() {
        conv.append(new UserMessage("hi", 1000L));
        conv.append(new AssistantMessage("hey", 2000L));
        conv.append(new SystemMessage("go", 3000L));
        assertEquals(3, conv.size());
        assertEquals("hi", conv.get(0).content());
        assertEquals("hey", conv.get(1).content());
        assertEquals("go", conv.get(2).content());
    }

    @Test
    void historyReturnsUnmodifiableList() {
        conv.append(new UserMessage("a", 1L));
        assertThrows(UnsupportedOperationException.class,
            () -> conv.history().add(new UserMessage("b", 2L)));
    }

    @Test
    void historyReturnsInsertionOrder() {
        conv.append(new UserMessage("first", 100L));
        conv.append(new UserMessage("second", 200L));
        assertEquals(2, conv.history().size());
        assertEquals("first", conv.history().get(0).content());
        assertEquals("second", conv.history().get(1).content());
    }

    @Test
    void getThrowsForInvalidIndex() {
        assertThrows(IndexOutOfBoundsException.class, () -> conv.get(0));
        conv.append(new UserMessage("x", 1L));
        assertThrows(IndexOutOfBoundsException.class, () -> conv.get(1));
        assertThrows(IndexOutOfBoundsException.class, () -> conv.get(-1));
    }

    @Test
    void clearRemovesAllMessages() {
        conv.append(new UserMessage("a", 1L));
        conv.append(new UserMessage("b", 2L));
        assertEquals(2, conv.size());
        conv.clear();
        assertEquals(0, conv.size());
        assertTrue(conv.history().isEmpty());
    }

    @Test
    void sizeOnEmptyConversation() {
        assertEquals(0, conv.size());
    }
}
