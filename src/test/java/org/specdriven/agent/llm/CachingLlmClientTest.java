package org.specdriven.agent.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.specdriven.agent.agent.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CachingLlmClientTest {

    private StubLlmClient delegate;
    private StubCache cache;
    private CachingLlmClient client;

    @BeforeEach
    void setUp() {
        delegate = new StubLlmClient();
        cache = new StubCache();
        client = new CachingLlmClient(delegate, cache, "session1", "agent1");
    }

    // -------------------------------------------------------------------------
    // Cache hit / miss
    // -------------------------------------------------------------------------

    @Test
    void chat_cacheHit_skipsDelegate() {
        LlmRequest request = LlmRequest.of(List.of(new UserMessage("hello", 1)));
        LlmResponse expected = new LlmResponse.TextResponse("cached", new LlmUsage(10, 5, 15), "stop");

        // Pre-populate cache
        String key = CacheKeyGenerator.generate(request);
        cache.store.put(key, CachingLlmClient.serializeResponse(expected));

        LlmResponse result = client.chat(request);
        assertEquals("cached", ((LlmResponse.TextResponse) result).content());
        assertEquals(0, delegate.callCount.get()); // delegate not called
    }

    @Test
    void chat_cacheMiss_callsDelegateAndCaches() {
        LlmRequest request = LlmRequest.of(List.of(new UserMessage("hello", 1)));
        LlmResponse response = new LlmResponse.TextResponse("world", new LlmUsage(100, 50, 150), "stop");
        delegate.response = response;

        LlmResponse result = client.chat(request);
        assertEquals("world", ((LlmResponse.TextResponse) result).content());
        assertEquals(1, delegate.callCount.get());
        assertEquals(1, cache.putCount.get());

        // Second call should hit cache
        LlmResponse result2 = client.chat(request);
        assertEquals("world", ((LlmResponse.TextResponse) result2).content());
        assertEquals(1, delegate.callCount.get()); // still 1
    }

    @Test
    void chat_recordsUsage() {
        LlmRequest request = LlmRequest.of(List.of(new UserMessage("hello", 1)));
        delegate.response = new LlmResponse.TextResponse("world", new LlmUsage(100, 50, 150), "stop");

        client.chat(request);
        assertEquals(1, cache.usageRecords.size());
        UsageRecord r = cache.usageRecords.get(0);
        assertEquals("session1", r.sessionId());
        assertEquals("agent1", r.agentName());
        assertEquals(100, r.promptTokens());
    }

    // -------------------------------------------------------------------------
    // Streaming
    // -------------------------------------------------------------------------

    @Test
    void chatStreaming_doesNotCacheButRecordsUsage() {
        LlmRequest request = LlmRequest.of(List.of(new UserMessage("hello", 1)));
        LlmResponse response = new LlmResponse.TextResponse("streamed", new LlmUsage(20, 10, 30), "stop");

        AtomicReference<LlmResponse> received = new AtomicReference<>();
        AtomicInteger tokens = new AtomicInteger();

        // Set up streaming delegate
        delegate.streamingResponse = response;

        client.chatStreaming(request, new LlmStreamCallback() {
            @Override public void onToken(String token) { tokens.incrementAndGet(); }
            @Override public void onComplete(LlmResponse r) { received.set(r); }
            @Override public void onError(Exception e) { fail("Unexpected error"); }
        });

        // Verify callback received
        assertEquals("streamed", ((LlmResponse.TextResponse) received.get()).content());
        // Verify NOT cached
        assertEquals(0, cache.putCount.get());
        // Verify usage recorded
        assertEquals(1, cache.usageRecords.size());
        assertEquals(20, cache.usageRecords.get(0).promptTokens());
    }

    // -------------------------------------------------------------------------
    // Response serialization round-trip
    // -------------------------------------------------------------------------

    @Test
    void serialization_textResponse_roundTrip() {
        LlmResponse original = new LlmResponse.TextResponse("hello", new LlmUsage(10, 5, 15), "stop");
        String json = CachingLlmClient.serializeResponse(original);
        LlmResponse restored = CachingLlmClient.deserializeResponse(json);

        assertInstanceOf(LlmResponse.TextResponse.class, restored);
        LlmResponse.TextResponse text = (LlmResponse.TextResponse) restored;
        assertEquals("hello", text.content());
        assertEquals("stop", text.finishReason());
        assertNotNull(text.usage());
        assertEquals(10, text.usage().promptTokens());
        assertEquals(5, text.usage().completionTokens());
        assertEquals(15, text.usage().totalTokens());
    }

    @Test
    void serialization_toolCallResponse_roundTrip() {
        ToolCall tc = new ToolCall("bash", Map.of("command", "ls"), "call1");
        LlmResponse original = new LlmResponse.ToolCallResponse(List.of(tc), new LlmUsage(20, 10, 30), "tool_calls");
        String json = CachingLlmClient.serializeResponse(original);
        LlmResponse restored = CachingLlmClient.deserializeResponse(json);

        assertInstanceOf(LlmResponse.ToolCallResponse.class, restored);
        LlmResponse.ToolCallResponse toolCall = (LlmResponse.ToolCallResponse) restored;
        assertEquals(1, toolCall.toolCalls().size());
        assertEquals("bash", toolCall.toolCalls().get(0).toolName());
        assertEquals("ls", toolCall.toolCalls().get(0).parameters().get("command"));
        assertEquals("call1", toolCall.toolCalls().get(0).callId());
        assertNotNull(toolCall.usage());
        assertEquals(30, toolCall.usage().totalTokens());
    }

    // -------------------------------------------------------------------------
    // Stubs
    // -------------------------------------------------------------------------

    private static class StubLlmClient implements LlmClient {
        final AtomicInteger callCount = new AtomicInteger();
        LlmResponse response = new LlmResponse.TextResponse("default");
        LlmResponse streamingResponse;

        @Override
        public LlmResponse chat(List<Message> messages) {
            callCount.incrementAndGet();
            return response;
        }

        @Override
        public void chatStreaming(LlmRequest request, LlmStreamCallback callback) {
            callCount.incrementAndGet();
            callback.onToken("tok");
            if (streamingResponse != null) {
                callback.onComplete(streamingResponse);
            }
        }
    }

    private static class StubCache implements LlmCache {
        final java.util.concurrent.ConcurrentHashMap<String, String> store = new java.util.concurrent.ConcurrentHashMap<>();
        final AtomicInteger putCount = new AtomicInteger();
        final List<UsageRecord> usageRecords = new ArrayList<>();

        @Override
        public Optional<String> get(String key) {
            return Optional.ofNullable(store.get(key));
        }

        @Override
        public void put(String key, String responseJson, long ttlMs) {
            putCount.incrementAndGet();
            store.put(key, responseJson);
        }

        @Override
        public void invalidate(String key) {
            store.remove(key);
        }

        @Override
        public void clear() {
            store.clear();
        }

        @Override
        public void recordUsage(String sessionId, String agentName, String model, LlmUsage usage) {
            usageRecords.add(new UsageRecord(usageRecords.size() + 1, sessionId, agentName, model,
                    usage.promptTokens(), usage.completionTokens(), usage.totalTokens(), System.currentTimeMillis()));
        }

        @Override
        public List<UsageRecord> queryUsage(String sessionId, String agentName, long from, long to) {
            return List.of();
        }
    }
}
