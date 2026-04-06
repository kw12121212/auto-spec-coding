package org.specdriven.agent.agent;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.specdriven.agent.tool.Tool;

class SimpleAgentContextTest {

    @Test
    void allAccessors() {
        Conversation conv = new Conversation();
        SimpleAgentContext ctx = new SimpleAgentContext(
            "session-1", Map.of("k", "v"), Map.of(), conv);

        assertEquals("session-1", ctx.sessionId());
        assertEquals("v", ctx.config().get("k"));
        assertTrue(ctx.toolRegistry().isEmpty());
        assertSame(conv, ctx.conversation());
    }

    @Test
    void conversationIsNonNull() {
        Conversation conv = new Conversation();
        SimpleAgentContext ctx = new SimpleAgentContext(
            "s", Map.of(), Map.of(), conv);
        assertNotNull(ctx.conversation());
    }
}
