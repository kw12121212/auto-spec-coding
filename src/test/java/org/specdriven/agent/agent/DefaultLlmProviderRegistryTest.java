package org.specdriven.agent.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DefaultLlmProviderRegistryTest {

    // --- fromConfig ---

    @Test
    void fromConfig_registersProviders(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("config.yaml");
        Files.writeString(file, """
            llm:
              providers:
                openai-main:
                  baseUrl: "https://api.openai.com/v1"
                  apiKey: "sk-test"
                  model: "gpt-4"
                deepseek:
                  baseUrl: "https://api.deepseek.com/v1"
                  apiKey: "sk-ds"
                  model: "deepseek-chat"
              default: "openai-main"
            """);
        org.specdriven.agent.config.Config config =
                org.specdriven.agent.config.ConfigLoader.load(file);

        LlmProviderFactory stubFactory = cfg -> new StubProvider(cfg.baseUrl(), cfg.apiKey());
        Map<String, LlmProviderFactory> factories = Map.of("openai", stubFactory);

        DefaultLlmProviderRegistry registry =
                DefaultLlmProviderRegistry.fromConfig(config, factories);

        assertEquals(2, registry.providerNames().size());
        assertTrue(registry.providerNames().contains("openai-main"));
        assertTrue(registry.providerNames().contains("deepseek"));
        assertEquals("sk-test", registry.defaultProvider().config().apiKey());
        registry.close();
    }

    @Test
    void fromConfig_withSkillRouting(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("config.yaml");
        Files.writeString(file, """
            llm:
              providers:
                openai:
                  baseUrl: "https://api.openai.com/v1"
                  apiKey: "sk-test"
                  model: "gpt-4"
                claude:
                  baseUrl: "https://api.anthropic.com"
                  apiKey: "sk-ant"
                  model: "claude-sonnet-4-6-20250514"
              default: "openai"
              skill-routing:
                code-review:
                  provider: "claude"
                  model: "claude-opus-4-6-20250514"
                code-gen:
                  provider: "openai"
            """);
        org.specdriven.agent.config.Config config =
                org.specdriven.agent.config.ConfigLoader.load(file);

        LlmProviderFactory stubFactory = cfg -> new StubProvider(cfg.baseUrl(), cfg.apiKey());
        Map<String, LlmProviderFactory> factories = Map.of(
                "openai", stubFactory,
                "claude", stubFactory
        );

        DefaultLlmProviderRegistry registry =
                DefaultLlmProviderRegistry.fromConfig(config, factories);

        SkillRoute reviewRoute = registry.route("code-review");
        assertNotNull(reviewRoute);
        assertEquals("claude", reviewRoute.providerName());
        assertEquals("claude-opus-4-6-20250514", reviewRoute.modelOverride());

        SkillRoute genRoute = registry.route("code-gen");
        assertNotNull(genRoute);
        assertEquals("openai", genRoute.providerName());
        assertNull(genRoute.modelOverride());

        registry.close();
    }

    @Test
    void fromConfig_missingDefault_usesFirstProvider(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("config.yaml");
        Files.writeString(file, """
            llm:
              providers:
                deepseek:
                  baseUrl: "https://api.deepseek.com/v1"
                  apiKey: "sk-ds"
                  model: "deepseek-chat"
            """);
        org.specdriven.agent.config.Config config =
                org.specdriven.agent.config.ConfigLoader.load(file);

        LlmProviderFactory stubFactory = cfg -> new StubProvider(cfg.baseUrl(), cfg.apiKey());
        DefaultLlmProviderRegistry registry =
                DefaultLlmProviderRegistry.fromConfig(config, Map.of("openai", stubFactory));

        assertSame(registry.provider("deepseek"), registry.defaultProvider());
        registry.close();
    }

    // --- thread safety ---

    @Test
    void concurrentRegisterAndLookup() throws Exception {
        DefaultLlmProviderRegistry registry = new DefaultLlmProviderRegistry();
        int count = 100;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(count);
        AtomicInteger errors = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(10);
        for (int i = 0; i < count; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    start.await();
                    String name = "provider-" + idx;
                    registry.register(name, new StubProvider("https://api.example.com/" + idx, "key-" + idx));
                    assertSame(registry.provider(name), registry.provider(name));
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertEquals(0, errors.get());
        assertEquals(count, registry.providerNames().size());
        executor.shutdown();
        registry.close();
    }

    // --- close cascades ---

    @Test
    void close_closesAllProviders() {
        DefaultLlmProviderRegistry registry = new DefaultLlmProviderRegistry();
        StubProvider p1 = new StubProvider("https://a.com", "k1");
        StubProvider p2 = new StubProvider("https://b.com", "k2");
        registry.register("a", p1);
        registry.register("b", p2);

        assertFalse(p1.closed);
        assertFalse(p2.closed);

        registry.close();

        assertTrue(p1.closed);
        assertTrue(p2.closed);
        assertTrue(registry.providerNames().isEmpty());
        assertNull(registry.route("anything"));
    }

    @Test
    void defaultSnapshot_usesDefaultProviderConfig() {
        DefaultLlmProviderRegistry registry = new DefaultLlmProviderRegistry();
        StubProvider provider = new StubProvider("https://api.openai.com/v1", "key");
        registry.register("openai", provider);

        LlmConfigSnapshot snapshot = registry.defaultSnapshot();

        assertEquals("openai", snapshot.providerName());
        assertEquals(provider.config().baseUrl(), snapshot.baseUrl());
        assertEquals(provider.config().model(), snapshot.model());
        assertEquals(provider.config().timeout(), snapshot.timeout());
        assertEquals(provider.config().maxRetries(), snapshot.maxRetries());
        registry.close();
    }

    @Test
    void replaceSessionSnapshot_fallsBackWhenCleared() {
        DefaultLlmProviderRegistry registry = new DefaultLlmProviderRegistry();
        registry.register("openai", new StubProvider("https://api.openai.com/v1", "key-a"));
        registry.register("claude", new StubProvider("https://api.anthropic.com/v1", "key-b"));
        registry.setDefault("openai");

        LlmConfigSnapshot sessionSnapshot = new LlmConfigSnapshot(
                "claude",
                "https://api.anthropic.com/v1",
                "claude-opus",
                20,
                1);
        registry.replaceSessionSnapshot("session-a", sessionSnapshot);

        assertEquals(sessionSnapshot, registry.snapshot("session-a"));

        registry.clearSessionSnapshot("session-a");

        assertEquals("openai", registry.snapshot("session-a").providerName());
        registry.close();
    }

    @Test
    void replaceDefaultSnapshot_updatesFutureResolutionAtomically() {
        DefaultLlmProviderRegistry registry = new DefaultLlmProviderRegistry();
        registry.register("openai", new StubProvider("https://api.openai.com/v1", "key-a"));
        registry.setDefault("openai");

        LlmConfigSnapshot original = registry.defaultSnapshot();
        LlmConfigSnapshot replacement = new LlmConfigSnapshot(
                "openai",
                "https://api.alt.example/v1",
                "gpt-4.1",
                25,
                4);

        registry.replaceDefaultSnapshot(replacement);

        assertEquals(original.providerName(), replacement.providerName());
        assertNotEquals(original.baseUrl(), registry.defaultSnapshot().baseUrl());
        assertEquals(replacement, registry.defaultSnapshot());
        registry.close();
    }

    @Test
    void createClientForSession_bindsSnapshotPerRequest() {
        DefaultLlmProviderRegistry registry = new DefaultLlmProviderRegistry();
        SnapshotRecordingProvider provider = new SnapshotRecordingProvider(new LlmConfig(
                "https://api.openai.com/v1", "key-a", "gpt-4", 20, 1));
        registry.register("openai", provider);
        registry.setDefault("openai");

        LlmClient client = registry.createClientForSession("session-a");
        LlmConfigSnapshot first = new LlmConfigSnapshot(
                "openai",
                "https://api.first.example/v1",
                "gpt-4",
                20,
                1);
        LlmConfigSnapshot second = new LlmConfigSnapshot(
                "openai",
                "https://api.second.example/v1",
                "gpt-4.1",
                30,
                2);

        registry.replaceSessionSnapshot("session-a", first);
        client.chat(List.of(new UserMessage("first", 0)));

        registry.replaceSessionSnapshot("session-a", second);
        client.chat(List.of(new UserMessage("second", 0)));

        assertEquals(List.of(first, second), provider.snapshotsSeen);
        registry.close();
    }

    // --- stub ---

    static class StubProvider implements LlmProvider {
        final LlmConfig config;
        volatile boolean closed;

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

    static final class SnapshotRecordingProvider implements LlmProvider {
        private final LlmConfig config;
        private final List<LlmConfigSnapshot> snapshotsSeen = new CopyOnWriteArrayList<>();

        SnapshotRecordingProvider(LlmConfig config) {
            this.config = config;
        }

        @Override
        public LlmConfig config() {
            return config;
        }

        @Override
        public LlmClient createClient() {
            throw new UnsupportedOperationException("snapshot-aware path expected");
        }

        @Override
        public LlmClient createClient(LlmConfigSnapshot snapshot) {
            snapshotsSeen.add(snapshot);
            return new LlmClient() {
                @Override
                public LlmResponse chat(List<Message> messages) {
                    return new LlmResponse.TextResponse(snapshot.model(), null, null);
                }

                @Override
                public LlmResponse chat(LlmRequest request) {
                    return new LlmResponse.TextResponse(snapshot.model(), null, null);
                }
            };
        }

        @Override
        public void close() {
        }
    }
}
