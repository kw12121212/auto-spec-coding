package org.specdriven.agent.tool;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ToolResultTest {

    @Test
    void successResult() {
        ToolResult.Success result = new ToolResult.Success("hello world");
        assertEquals("hello world", result.output());
    }

    @Test
    void errorResultWithCause() {
        RuntimeException cause = new RuntimeException("boom");
        ToolResult.Error result = new ToolResult.Error("something failed", cause);
        assertEquals("something failed", result.message());
        assertSame(cause, result.cause());
    }

    @Test
    void errorResultWithoutCause() {
        ToolResult.Error result = new ToolResult.Error("simple error");
        assertEquals("simple error", result.message());
        assertNull(result.cause());
    }

    @Test
    void sealedTypePatternMatching() {
        ToolResult success = new ToolResult.Success("ok");
        ToolResult error = new ToolResult.Error("fail");

        assertTrue(success instanceof ToolResult.Success);
        assertTrue(error instanceof ToolResult.Error);
    }
}
