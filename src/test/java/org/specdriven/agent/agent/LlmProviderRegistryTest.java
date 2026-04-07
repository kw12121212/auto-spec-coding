package org.specdriven.agent.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LlmProviderRegistryTest {

    private DefaultLlmProviderRegistry registry;
    private StubProvider openai;
    private StubProvider claude;

    @BeforeEach
    void setUp() {
        registry = new DefaultLlmProviderRegistry();
        openai = new StubProvider("https://api.openai.com", "sk-oai");
        claude = new StubProvider("https://api.anthropic.com", "sk-ant");
    }

    // --- register ---

    @Test
    void register_andLookup() {
        registry.register("openai", openai);
        assertSame(openai, registry.provider("openai"));
    }

    @Test
    void register_nullName_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(null, openai));
    }

    @Test
    void register_blankName_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.register("  ", openai));
    }

    @Test
    void register_nullProvider_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.register("openai", null));
    }

    @Test
    void register_duplicateName_throws() {
        registry.register("openai", openai);
        assertThrows(IllegalArgumentException.class,
                () -> registry.register("openai", claude));
    }

    // --- provider ---

    @Test
    void provider_unknownName_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.provider("unknown"));
    }

    // --- providerNames ---

    @Test
    void providerNames_returnsAll() {
        registry.register("openai", openai);
        registry.register("claude", claude);
        assertEquals(Set.of("openai", "claude"), registry.providerNames());
    }

    @Test
    void providerNames_empty() {
        assertTrue(registry.providerNames().isEmpty());
    }

    // --- remove ---

    @Test
    void remove_closesProvider() {
        registry.register("openai", openai);
        registry.remove("openai");
        assertTrue(openai.closed);
        assertThrows(IllegalArgumentException.class, () -> registry.provider("openai"));
    }

    @Test
    void remove_unknownName_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.remove("unknown"));
    }

    @Test
    void remove_default_clearsDefault() {
        registry.register("openai", openai);
        registry.register("claude", claude);
        registry.setDefault("openai");
        registry.remove("openai");
        // default falls back to first remaining
        assertSame(claude, registry.defaultProvider());
    }

    // --- close ---

    @Test
    void close_closesAll() {
        registry.register("openai", openai);
        registry.register("claude", claude);
        registry.close();
        assertTrue(openai.closed);
        assertTrue(claude.closed);
        assertTrue(registry.providerNames().isEmpty());
    }

    // --- default provider ---

    @Test
    void defaultProvider_explicit() {
        registry.register("openai", openai);
        registry.register("claude", claude);
        registry.setDefault("claude");
        assertSame(claude, registry.defaultProvider());
    }

    @Test
    void defaultProvider_fallbackToFirst() {
        registry.register("openai", openai);
        assertSame(openai, registry.defaultProvider());
    }

    @Test
    void defaultProvider_empty_throws() {
        assertThrows(IllegalStateException.class, () -> registry.defaultProvider());
    }

    @Test
    void setDefault_unknownName_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.setDefault("unknown"));
    }

    // --- skill routing ---

    @Test
    void addSkillRoute_andRoute() {
        SkillRoute route = new SkillRoute("claude", "claude-opus-4-6-20250514");
        registry.addSkillRoute("code-review", route);
        SkillRoute result = registry.route("code-review");
        assertNotNull(result);
        assertEquals("claude", result.providerName());
        assertEquals("claude-opus-4-6-20250514", result.modelOverride());
    }

    @Test
    void route_unknown_returnsNull() {
        assertNull(registry.route("unknown"));
    }

    @Test
    void addSkillRoute_nullSkillName_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.addSkillRoute(null, new SkillRoute("x", null)));
    }

    @Test
    void addSkillRoute_nullRoute_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.addSkillRoute("skill", null));
    }

    // --- stub provider ---

    static class StubProvider implements LlmProvider {
        final LlmConfig config;
        boolean closed;

        StubProvider(String baseUrl, String apiKey) {
            config = new LlmConfig(baseUrl, apiKey, "model", 60, 3);
        }

        @Override
        public LlmConfig config() { return config; }

        @Override
        public LlmClient createClient() {
            return new LlmClient() {
                @Override
                public LlmResponse chat(List<Message> messages) {
                    return new LlmResponse.TextResponse("stub", null, null);
                }
            };
        }

        @Override
        public void close() { closed = true; }
    }
}
