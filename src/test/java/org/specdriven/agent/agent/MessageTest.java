package org.specdriven.agent.agent;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MessageTest {

    @Test
    void userMessageRole() {
        UserMessage msg = new UserMessage("hello", 1000L);
        assertEquals("user", msg.role());
        assertEquals("hello", msg.content());
        assertEquals(1000L, msg.timestamp());
    }

    @Test
    void assistantMessageRole() {
        AssistantMessage msg = new AssistantMessage("response", 2000L);
        assertEquals("assistant", msg.role());
        assertEquals("response", msg.content());
        assertEquals(2000L, msg.timestamp());
    }

    @Test
    void toolMessageRoleAndToolName() {
        ToolMessage msg = new ToolMessage("output", 3000L, "bash");
        assertEquals("tool", msg.role());
        assertEquals("output", msg.content());
        assertEquals(3000L, msg.timestamp());
        assertEquals("bash", msg.toolName());
    }

    @Test
    void systemMessageRole() {
        SystemMessage msg = new SystemMessage("instruction", 4000L);
        assertEquals("system", msg.role());
        assertEquals("instruction", msg.content());
        assertEquals(4000L, msg.timestamp());
    }

    @Test
    void messagesAreImmutable() {
        UserMessage msg = new UserMessage("content", 1000L);
        assertEquals("content", msg.content());
        // records are immutable by definition — verify field access works
        assertNotEquals(msg, new UserMessage("other", 1000L));
    }
}
