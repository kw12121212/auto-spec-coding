package org.specdriven.agent.tool;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ToolParameterTest {

    @Test
    void constructionAndFieldAccess() {
        ToolParameter param = new ToolParameter("path", "string", "File path to read", true);
        assertEquals("path", param.name());
        assertEquals("string", param.type());
        assertEquals("File path to read", param.description());
        assertTrue(param.required());
    }

    @Test
    void optionalParameter() {
        ToolParameter param = new ToolParameter("verbose", "boolean", "Enable verbose output", false);
        assertFalse(param.required());
    }

    @Test
    void equality() {
        ToolParameter a = new ToolParameter("name", "string", "desc", true);
        ToolParameter b = new ToolParameter("name", "string", "desc", true);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
