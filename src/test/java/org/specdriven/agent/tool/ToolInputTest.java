package org.specdriven.agent.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ToolInputTest {

    @Test
    void constructionAndParameterAccess() {
        ToolInput input = new ToolInput(Map.of("key", "value"));
        assertEquals("value", input.parameters().get("key"));
    }

    @Test
    void parametersAreImmutable() {
        ToolInput input = new ToolInput(Map.of("key", "value"));
        assertThrows(UnsupportedOperationException.class, () ->
                input.parameters().put("other", "value"));
    }

    @Test
    void emptyInput() {
        ToolInput input = ToolInput.empty();
        assertTrue(input.parameters().isEmpty());
    }
}
