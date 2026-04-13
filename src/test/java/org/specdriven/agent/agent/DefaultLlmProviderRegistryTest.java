package org.specdriven.agent.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.event.SimpleEventBus;
import org.specdriven.agent.llm.LealoneRuntimeLlmConfigStore;

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
import java.util.UUID;

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
    void replaceDefaultSnapshot_publishesConfigChangedEventWithDefaultMetadata() {
        SimpleEventBus eventBus = new SimpleEventBus();
        List<Event> events = new CopyOnWriteArrayList<>();
        eventBus.subscribe(EventType.LLM_CONFIG_CHANGED, events::add);

        DefaultLlmProviderRegistry registry = new DefaultLlmProviderRegistry(null, eventBus);
        registry.register("openai", new StubProvider("https://api.openai.com/v1", "key-a"));
        registry.setDefault("openai");

        registry.replaceDefaultSnapshot(new LlmConfigSnapshot(
                "openai",
                "https://api.alt.example/v1",
                "gpt-4.1",
                25,
                4));

        assertEquals(1, events.size());
        Event event = events.get(0);
        assertEquals(EventType.LLM_CONFIG_CHANGED, event.type());
        assertEquals("llm-runtime-config", event.source());
        assertEquals("default", event.metadata().get("scope"));
        assertFalse(event.metadata().containsKey("sessionId"));
        assertEquals("openai", event.metadata().get("provider"));
        assertEquals("baseUrl,model,timeout,maxRetries", event.metadata().get("changedKeys"));
        registry.close();
    }

    @Test
    void replaceSessionSnapshot_publishesConfigChangedEventWithSessionMetadata() {
        SimpleEventBus eventBus = new SimpleEventBus();
        List<Event> events = new CopyOnWriteArrayList<>();
        eventBus.subscribe(EventType.LLM_CONFIG_CHANGED, events::add);

        DefaultLlmProviderRegistry registry = new DefaultLlmProviderRegistry(null, eventBus);
        registry.register("openai", new StubProvider("https://api.openai.com/v1", "key-a"));
        registry.register("claude", new StubProvider("https://api.anthropic.com/v1", "key-b"));
        registry.setDefault("openai");

        registry.replaceSessionSnapshot("session-a", new LlmConfigSnapshot(
                "claude",
                "https://api.anthropic.com/v1",
                "claude-opus",
                20,
                1));

        assertEquals(1, events.size());
        Event event = events.get(0);
        assertEquals("session", event.metadata().get("scope"));
        assertEquals("session-a", event.metadata().get("sessionId"));
        assertEquals("claude", event.metadata().get("provider"));
        assertEquals("provider,baseUrl,model,timeout,maxRetries", event.metadata().get("changedKeys"));
        registry.close();
    }

    @Test
    void applySetLlmStatement_updatesSupportedSessionParametersAtomically() {
        DefaultLlmProviderRegistry registry = new DefaultLlmProviderRegistry();
        registry.register("openai", new StubProvider("https://api.openai.com/v1", "key-a"));
        registry.register("claude", new StubProvider("https://api.anthropic.com/v1", "key-b"));
        registry.setDefault("openai");

        LlmConfigSnapshot updated = registry.applySetLlmStatement(
                "session-a",
                "SET LLM provider='claude', model='claude-sonnet', base_url='https://api.alt.anthropic.com/v1', timeout=45, max_retries=5");

        assertEquals(updated, registry.snapshot("session-a"));
        assertEquals("claude", updated.providerName());
        assertEquals("claude-sonnet", updated.model());
        assertEquals("https://api.alt.anthropic.com/v1", updated.baseUrl());
        assertEquals(45, updated.timeout());
        assertEquals(5, updated.maxRetries());
        assertEquals("openai", registry.defaultSnapshot().providerName());
        registry.close();
    }

    @Test
    void applySetLlmStatement_preservesUnspecifiedValues() {
        DefaultLlmProviderRegistry registry = new DefaultLlmProviderRegistry();
        registry.register("openai", new StubProvider("https://api.openai.com/v1", "key-a"));
        registry.setDefault("openai");

        LlmConfigSnapshot original = registry.snapshot("session-a");
        LlmConfigSnapshot updated = registry.applySetLlmStatement("session-a", "SET LLM model='gpt-4.1-mini'");

        assertEquals(original.providerName(), updated.providerName());
        assertEquals(original.baseUrl(), updated.baseUrl());
        assertEquals("gpt-4.1-mini", updated.model());
        assertEquals(original.timeout(), updated.timeout());
        assertEquals(original.maxRetries(), updated.maxRetries());
        registry.close();
    }

    @Test
    void applySetLlmStatement_rejectsUnsupportedOrInvalidAssignmentsWithoutPartialUpdate() {
        DefaultLlmProviderRegistry registry = new DefaultLlmProviderRegistry();
        registry.register("openai", new StubProvider("https://api.openai.com/v1", "key-a"));
        registry.setDefault("openai");

        LlmConfigSnapshot before = registry.snapshot("session-a");

        assertThrows(SetLlmSqlException.class,
                () -> registry.applySetLlmStatement("session-a", "SET LLM api_key='secret'"));
        assertEquals(before, registry.snapshot("session-a"));

        assertThrows(SetLlmSqlException.class,
                () -> registry.applySetLlmStatement("session-a", "SET LLM timeout=0, model='gpt-4.1'"));
        assertEquals(before, registry.snapshot("session-a"));
        registry.close();
    }

    @Test
    void applySetLlmStatement_isSessionScoped() {
        DefaultLlmProviderRegistry registry = new DefaultLlmProviderRegistry();
        registry.register("openai", new StubProvider("https://api.openai.com/v1", "key-a"));
        registry.setDefault("openai");

        LlmConfigSnapshot sessionB = registry.snapshot("session-b");

        registry.applySetLlmStatement("session-a", "SET LLM model='gpt-4.1' , timeout=30");

        assertEquals("gpt-4.1", registry.snapshot("session-a").model());
        assertEquals(30, registry.snapshot("session-a").timeout());
        assertEquals(sessionB, registry.snapshot("session-b"));
        registry.close();
    }

    @Test
    void applySetLlmStatement_publishesConfigChangedEvent() {
        SimpleEventBus eventBus = new SimpleEventBus();
        List<Event> events = new CopyOnWriteArrayList<>();
        eventBus.subscribe(EventType.LLM_CONFIG_CHANGED, events::add);

        DefaultLlmProviderRegistry registry = new DefaultLlmProviderRegistry(null, eventBus);
        registry.register("openai", new StubProvider("https://api.openai.com/v1", "key-a"));
        registry.register("claude", new StubProvider("https://api.anthropic.com/v1", "key-b"));
        registry.setDefault("openai");

        registry.applySetLlmStatement("session-a", "SET LLM provider='claude', model='claude-sonnet', timeout=45");

        assertEquals(1, events.size());
        Event event = events.get(0);
        assertEquals("session", event.metadata().get("scope"));
        assertEquals("session-a", event.metadata().get("sessionId"));
        assertEquals("claude", event.metadata().get("provider"));
        assertEquals("provider,model,timeout", event.metadata().get("changedKeys"));
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
    void clearSessionSnapshot_publishesFallbackEvent() {
        SimpleEventBus eventBus = new SimpleEventBus();
        List<Event> events = new CopyOnWriteArrayList<>();
        eventBus.subscribe(EventType.LLM_CONFIG_CHANGED, events::add);

        DefaultLlmProviderRegistry registry = new DefaultLlmProviderRegistry(null, eventBus);
        registry.register("openai", new StubProvider("https://api.openai.com/v1", "key-a"));
        registry.register("claude", new StubProvider("https://api.anthropic.com/v1", "key-b"));
        registry.setDefault("openai");
        registry.replaceSessionSnapshot("session-a", new LlmConfigSnapshot(
                "claude",
                "https://api.anthropic.com/v1",
                "claude-opus",
                20,
                1));
        events.clear();

        registry.clearSessionSnapshot("session-a");

        assertEquals(1, events.size());
        Event event = events.get(0);
        assertEquals("session", event.metadata().get("scope"));
        assertEquals("session-a", event.metadata().get("sessionId"));
        assertEquals("openai", event.metadata().get("provider"));
        assertEquals("provider,baseUrl,model,timeout,maxRetries", event.metadata().get("changedKeys"));
        registry.close();
    }

    @Test
    void failedRuntimeUpdateDoesNotPublishConfigChangedEvent() {
        SimpleEventBus eventBus = new SimpleEventBus();
        List<Event> events = new CopyOnWriteArrayList<>();
        eventBus.subscribe(EventType.LLM_CONFIG_CHANGED, events::add);

        DefaultLlmProviderRegistry registry = new DefaultLlmProviderRegistry(null, eventBus);
        registry.register("openai", new StubProvider("https://api.openai.com/v1", "key-a"));
        registry.setDefault("openai");

        assertThrows(SetLlmSqlException.class,
                () -> registry.applySetLlmStatement("session-a", "SET LLM timeout=0, model='gpt-4.1'"));
        assertTrue(events.isEmpty());
        registry.close();
    }

    @Test
    void defaultSnapshot_recoversPersistedDefaultWhenStoreConfigured() {
        String jdbcUrl = jdbcUrl("llm_registry_recovery");
        LealoneRuntimeLlmConfigStore firstStore = new LealoneRuntimeLlmConfigStore(jdbcUrl);
        firstStore.persistDefaultSnapshot(new LlmConfigSnapshot(
                "openai",
                "https://api.persisted.example/v1",
                "gpt-4.1",
                45,
                5));

        DefaultLlmProviderRegistry registry = new DefaultLlmProviderRegistry(new LealoneRuntimeLlmConfigStore(jdbcUrl));
        registry.register("openai", new StubProvider("https://api.openai.com/v1", "key-a"));
        registry.setDefault("openai");

        assertEquals("https://api.persisted.example/v1", registry.defaultSnapshot().baseUrl());
        assertEquals("gpt-4.1", registry.defaultSnapshot().model());
        registry.close();
    }

    @Test
    void replaceDefaultSnapshot_persistsDefaultWithoutChangingSessionOverrides() {
        String jdbcUrl = jdbcUrl("llm_registry_store");
        DefaultLlmProviderRegistry registry = new DefaultLlmProviderRegistry(new LealoneRuntimeLlmConfigStore(jdbcUrl));
        registry.register("openai", new StubProvider("https://api.openai.com/v1", "key-a"));
        registry.register("claude", new StubProvider("https://api.anthropic.com/v1", "key-b"));
        registry.setDefault("openai");

        LlmConfigSnapshot sessionSnapshot = new LlmConfigSnapshot(
                "claude",
                "https://api.anthropic.com/v1",
                "claude-opus",
                20,
                1);
        LlmConfigSnapshot persistedDefault = new LlmConfigSnapshot(
                "openai",
                "https://api.persisted.example/v1",
                "gpt-4.1",
                40,
                2);

        registry.replaceSessionSnapshot("session-a", sessionSnapshot);
        registry.replaceDefaultSnapshot(persistedDefault);

        assertEquals(sessionSnapshot, registry.snapshot("session-a"));
        registry.clearSessionSnapshot("session-a");
        assertEquals(persistedDefault, registry.snapshot("session-a"));
        registry.close();

        LealoneRuntimeLlmConfigStore recoveredStore = new LealoneRuntimeLlmConfigStore(jdbcUrl);
        assertEquals(persistedDefault, recoveredStore.loadDefaultSnapshot().orElseThrow());
    }

    @Test
    void defaultSnapshot_fallsBackWhenPersistedProviderIsUnavailable() {
        String jdbcUrl = jdbcUrl("llm_registry_missing_provider");
        LealoneRuntimeLlmConfigStore store = new LealoneRuntimeLlmConfigStore(jdbcUrl);
        store.persistDefaultSnapshot(new LlmConfigSnapshot(
                "claude",
                "https://api.anthropic.com/v1",
                "claude-opus",
                20,
                1));

        DefaultLlmProviderRegistry registry = new DefaultLlmProviderRegistry(new LealoneRuntimeLlmConfigStore(jdbcUrl));
        registry.register("openai", new StubProvider("https://api.openai.com/v1", "key-a"));
        registry.setDefault("openai");

        assertEquals("openai", registry.defaultSnapshot().providerName());
        assertEquals("https://api.openai.com/v1", registry.defaultSnapshot().baseUrl());
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

    @Test
    void createClientForSession_usesReplacementProviderForLaterRequests() {
        DefaultLlmProviderRegistry registry = new DefaultLlmProviderRegistry();
        SnapshotRecordingProvider openaiProvider = new SnapshotRecordingProvider(
                new LlmConfig("https://api.openai.com/v1", "key-a", "gpt-4", 20, 1),
                "openai");
        SnapshotRecordingProvider claudeProvider = new SnapshotRecordingProvider(
                new LlmConfig("https://api.anthropic.com/v1", "key-b", "claude-sonnet", 20, 1),
                "claude");
        registry.register("openai", openaiProvider);
        registry.register("claude", claudeProvider);
        registry.setDefault("openai");

        LlmClient client = registry.createClientForSession("session-a");
        LlmConfigSnapshot first = new LlmConfigSnapshot(
                "openai",
                "https://api.first.example/v1",
                "gpt-4",
                20,
                1);
        LlmConfigSnapshot second = new LlmConfigSnapshot(
                "claude",
                "https://api.second.example/v1",
                "claude-opus",
                30,
                2);

        registry.replaceSessionSnapshot("session-a", first);
        LlmResponse.TextResponse firstResponse = assertInstanceOf(
                LlmResponse.TextResponse.class,
                client.chat(List.of(new UserMessage("first", 0))));

        registry.replaceSessionSnapshot("session-a", second);
        LlmResponse.TextResponse secondResponse = assertInstanceOf(
                LlmResponse.TextResponse.class,
                client.chat(List.of(new UserMessage("second", 0))));

        assertEquals("openai:gpt-4", firstResponse.content());
        assertEquals("claude:claude-opus", secondResponse.content());
        assertEquals(List.of(first), openaiProvider.snapshotsSeen);
        assertEquals(List.of(second), claudeProvider.snapshotsSeen);
        registry.close();
    }

    @Test
    void createClientForSession_streamingRequestKeepsResolvedProviderUntilCompletion() throws Exception {
        DefaultLlmProviderRegistry registry = new DefaultLlmProviderRegistry();
        CountDownLatch streamStarted = new CountDownLatch(1);
        CountDownLatch releaseStream = new CountDownLatch(1);
        SnapshotRecordingProvider openaiProvider = new SnapshotRecordingProvider(
                new LlmConfig("https://api.openai.com/v1", "key-a", "gpt-4", 20, 1),
                "openai",
                streamStarted,
                releaseStream);
        SnapshotRecordingProvider claudeProvider = new SnapshotRecordingProvider(
                new LlmConfig("https://api.anthropic.com/v1", "key-b", "claude-sonnet", 20, 1),
                "claude");
        registry.register("openai", openaiProvider);
        registry.register("claude", claudeProvider);
        registry.setDefault("openai");

        LlmClient client = registry.createClientForSession("session-a");
        LlmConfigSnapshot first = new LlmConfigSnapshot(
                "openai",
                "https://api.first.example/v1",
                "gpt-4",
                20,
                1);
        LlmConfigSnapshot second = new LlmConfigSnapshot(
                "claude",
                "https://api.second.example/v1",
                "claude-opus",
                30,
                2);
        List<String> completions = new CopyOnWriteArrayList<>();
        List<Exception> errors = new CopyOnWriteArrayList<>();

        registry.replaceSessionSnapshot("session-a", first);
        Thread streamThread = new Thread(() -> client.chatStreaming(
                LlmRequest.of(List.of(new UserMessage("stream", 0))),
                new LlmStreamCallback() {
                    @Override
                    public void onToken(String token) {
                    }

                    @Override
                    public void onComplete(LlmResponse response) {
                        completions.add(assertInstanceOf(LlmResponse.TextResponse.class, response).content());
                    }

                    @Override
                    public void onError(Exception e) {
                        errors.add(e);
                    }
                }));
        streamThread.start();

        assertTrue(streamStarted.await(5, TimeUnit.SECONDS));
        registry.replaceSessionSnapshot("session-a", second);
        releaseStream.countDown();
        streamThread.join(TimeUnit.SECONDS.toMillis(5));
        assertFalse(streamThread.isAlive());

        LlmResponse.TextResponse laterResponse = assertInstanceOf(
                LlmResponse.TextResponse.class,
                client.chat(List.of(new UserMessage("after-stream", 0))));

        assertTrue(errors.isEmpty());
        assertEquals(List.of("openai:gpt-4"), completions);
        assertEquals("claude:claude-opus", laterResponse.content());
        assertEquals(List.of(first), openaiProvider.snapshotsSeen);
        assertEquals(List.of(second), claudeProvider.snapshotsSeen);
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
        private final String providerLabel;
        private final CountDownLatch streamStarted;
        private final CountDownLatch releaseStream;
        private final List<LlmConfigSnapshot> snapshotsSeen = new CopyOnWriteArrayList<>();

        SnapshotRecordingProvider(LlmConfig config) {
            this(config, "snapshot", null, null);
        }

        SnapshotRecordingProvider(LlmConfig config, String providerLabel) {
            this(config, providerLabel, null, null);
        }

        SnapshotRecordingProvider(
                LlmConfig config,
                String providerLabel,
                CountDownLatch streamStarted,
                CountDownLatch releaseStream) {
            this.config = config;
            this.providerLabel = providerLabel;
            this.streamStarted = streamStarted;
            this.releaseStream = releaseStream;
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
                    return new LlmResponse.TextResponse(providerLabel + ":" + snapshot.model(), null, null);
                }

                @Override
                public LlmResponse chat(LlmRequest request) {
                    return new LlmResponse.TextResponse(providerLabel + ":" + snapshot.model(), null, null);
                }

                @Override
                public void chatStreaming(LlmRequest request, LlmStreamCallback callback) {
                    try {
                        if (streamStarted != null) {
                            streamStarted.countDown();
                        }
                        if (releaseStream != null) {
                            releaseStream.await(5, TimeUnit.SECONDS);
                        }
                        callback.onComplete(new LlmResponse.TextResponse(providerLabel + ":" + snapshot.model(), null, null));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        callback.onError(e);
                    }
                }
            };
        }

        @Override
        public void close() {
        }
    }

    private static String jdbcUrl(String prefix) {
        String dbName = prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return "jdbc:lealone:embed:" + dbName + "?PERSISTENT=false";
    }
}
