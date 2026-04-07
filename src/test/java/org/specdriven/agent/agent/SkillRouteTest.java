package org.specdriven.agent.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillRouteTest {

    @Test
    void withModelOverride() {
        SkillRoute route = new SkillRoute("claude", "claude-opus-4-6-20250514");
        assertEquals("claude", route.providerName());
        assertEquals("claude-opus-4-6-20250514", route.modelOverride());
    }

    @Test
    void withoutModelOverride() {
        SkillRoute route = new SkillRoute("deepseek", null);
        assertEquals("deepseek", route.providerName());
        assertNull(route.modelOverride());
    }

    @Test
    void nullProviderName_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new SkillRoute(null, "model"));
    }

    @Test
    void blankProviderName_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new SkillRoute("  ", "model"));
    }
}
