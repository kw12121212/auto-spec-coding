package org.specdriven.agent.llm;

import org.specdriven.agent.agent.*;
import org.specdriven.agent.json.JsonWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Decorator that transparently adds caching to an {@link LlmClient}.
 * <p>
 * For {@link #chat(LlmRequest)}: checks the cache before calling the delegate.
 * For {@link #chatStreaming(LlmRequest, LlmStreamCallback)}: always delegates,
 * but wraps the callback to record token usage on completion.
 */
public class CachingLlmClient implements LlmClient {

    private static final long DEFAULT_TTL_MS = 5 * 60 * 1000L;

    private final LlmClient delegate;
    private final LlmCache cache;
    private final String sessionId;
    private final String agentName;

    /**
     * Creates a caching decorator around the given delegate.
     *
     * @param delegate   the real LLM client to delegate to on cache misses
     * @param cache      the cache backend
     * @param sessionId  optional session ID for usage tracking (may be null)
     * @param agentName  optional agent name for usage tracking (may be null)
     */
    public CachingLlmClient(LlmClient delegate, LlmCache cache, String sessionId, String agentName) {
        this.delegate = delegate;
        this.cache = cache;
        this.sessionId = sessionId;
        this.agentName = agentName;
    }

    @Override
    public LlmResponse chat(List<Message> messages) {
        return chat(LlmRequest.of(messages));
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        String key = CacheKeyGenerator.generate(request);

        // Check cache
        var cached = cache.get(key);
        if (cached.isPresent()) {
            return deserializeResponse(cached.get());
        }

        // Cache miss — call delegate
        LlmResponse response = delegate.chat(request);

        // Cache the response and record usage
        cache.put(key, serializeResponse(response), DEFAULT_TTL_MS);
        recordUsageIfPresent(response);

        return response;
    }

    @Override
    public void chatStreaming(LlmRequest request, LlmStreamCallback callback) {
        // Streaming does not cache responses, but records usage via wrapped callback
        delegate.chatStreaming(request, new LlmStreamCallback() {
            @Override
            public void onToken(String token) {
                callback.onToken(token);
            }

            @Override
            public void onComplete(LlmResponse response) {
                recordUsageIfPresent(response);
                callback.onComplete(response);
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Response serialization / deserialization
    // -------------------------------------------------------------------------

    static String serializeResponse(LlmResponse response) {
        if (response instanceof LlmResponse.TextResponse text) {
            JsonWriter w = JsonWriter.object()
                    .field("type", "text")
                    .field("content", text.content())
                    .field("finishReason", text.finishReason());
            if (text.usage() != null) {
                w.rawField("usage", serializeUsage(text.usage()));
            }
            return w.build();
        } else if (response instanceof LlmResponse.ToolCallResponse toolCall) {
            JsonWriter w = JsonWriter.object()
                    .field("type", "tool_call")
                    .field("finishReason", toolCall.finishReason());
            // Serialize tool calls
            List<String> items = new ArrayList<>();
            for (ToolCall tc : toolCall.toolCalls()) {
                items.add(serializeToolCall(tc));
            }
            w.arrayField("toolCalls", items);
            if (toolCall.usage() != null) {
                w.rawField("usage", serializeUsage(toolCall.usage()));
            }
            return w.build();
        }
        throw new IllegalArgumentException("Unknown LlmResponse type: " + response.getClass());
    }

    private static String serializeUsage(LlmUsage usage) {
        return JsonWriter.object()
                .field("promptTokens", usage.promptTokens())
                .field("completionTokens", usage.completionTokens())
                .field("totalTokens", usage.totalTokens())
                .build();
    }

    private static String serializeToolCall(ToolCall tc) {
        return JsonWriter.object()
                .field("toolName", tc.toolName())
                .rawField("parameters", JsonWriter.fromMap(tc.parameters()))
                .field("callId", tc.callId())
                .build();
    }

    static LlmResponse deserializeResponse(String json) {
        var map = org.specdriven.agent.json.JsonReader.parseObject(json);
        String type = (String) map.get("type");
        LlmUsage usage = deserializeUsage(map.get("usage"));

        if ("text".equals(type)) {
            String content = (String) map.get("content");
            String finishReason = (String) map.get("finishReason");
            return new LlmResponse.TextResponse(content, usage, finishReason);
        } else if ("tool_call".equals(type)) {
            List<Object> toolCallsRaw = (List<Object>) map.get("toolCalls");
            List<ToolCall> toolCalls = new ArrayList<>();
            if (toolCallsRaw != null) {
                for (Object item : toolCallsRaw) {
                    Map<String, Object> tcMap = (Map<String, Object>) item;
                    String toolName = (String) tcMap.get("toolName");
                    Map<String, Object> params = (Map<String, Object>) tcMap.get("parameters");
                    String callId = (String) tcMap.get("callId");
                    toolCalls.add(new ToolCall(toolName, params, callId));
                }
            }
            String finishReason = (String) map.get("finishReason");
            return new LlmResponse.ToolCallResponse(toolCalls, usage, finishReason);
        }
        throw new IllegalArgumentException("Unknown response type: " + type);
    }

    private static LlmUsage deserializeUsage(Object usageObj) {
        if (!(usageObj instanceof Map<?, ?> m)) return null;
        int promptTokens = ((Number) m.get("promptTokens")).intValue();
        int completionTokens = ((Number) m.get("completionTokens")).intValue();
        int totalTokens = ((Number) m.get("totalTokens")).intValue();
        return new LlmUsage(promptTokens, completionTokens, totalTokens);
    }

    // -------------------------------------------------------------------------
    // Usage recording
    // -------------------------------------------------------------------------

    private void recordUsageIfPresent(LlmResponse response) {
        LlmUsage usage = extractUsage(response);
        if (usage != null) {
            cache.recordUsage(sessionId, agentName, null, usage);
        }
    }

    private static LlmUsage extractUsage(LlmResponse response) {
        if (response instanceof LlmResponse.TextResponse text) {
            return text.usage();
        } else if (response instanceof LlmResponse.ToolCallResponse toolCall) {
            return toolCall.usage();
        }
        return null;
    }
}
