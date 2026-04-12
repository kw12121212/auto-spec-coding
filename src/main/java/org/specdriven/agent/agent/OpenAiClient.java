package org.specdriven.agent.agent;

import org.specdriven.agent.json.JsonReader;
import org.specdriven.agent.json.JsonWriter;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LlmClient implementation for OpenAI Chat Completions API and compatible endpoints.
 */
public class OpenAiClient implements LlmClient {

    private final LlmConfig config;
    private final HttpClient http;

    OpenAiClient(LlmConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.timeout()))
                .build();
    }

    LlmConfig config() {
        return config;
    }

    @Override
    public LlmResponse chat(List<Message> messages) {
        return chat(LlmRequest.of(messages));
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        String body = buildRequestBody(request, false);
        String url = config.baseUrl().endsWith("/")
                ? config.baseUrl() + "chat/completions"
                : config.baseUrl() + "/chat/completions";

        int attempt = 0;
        long backoffMs = 1000;
        while (true) {
            attempt++;
            try {
                HttpRequest httpReq = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + config.apiKey())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .timeout(Duration.ofSeconds(config.timeout()))
                        .build();

                HttpResponse<String> response = http.send(httpReq, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();

                if (status == 200) {
                    return parseResponse(response.body());
                }

                if (isRetryable(status) && attempt <= config.maxRetries()) {
                    long waitMs = backoffMs;
                    if (status == 429) {
                        String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
                        if (retryAfter != null) {
                            try { waitMs = Long.parseLong(retryAfter.trim()) * 1000; } catch (NumberFormatException ignored) {}
                        }
                    }
                    sleep(waitMs);
                    backoffMs = Math.min(backoffMs * 2, 30_000);
                    continue;
                }

                throw new RuntimeException("OpenAI API error " + status + ": " + response.body());

            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                if (attempt <= config.maxRetries()) {
                    sleep(backoffMs);
                    backoffMs = Math.min(backoffMs * 2, 30_000);
                    continue;
                }
                throw new RuntimeException("OpenAI request failed after " + attempt + " attempts", e);
            }
        }
    }

    @Override
    public void chatStreaming(LlmRequest request, LlmStreamCallback callback) {
        String body = buildRequestBody(request, true);
        String url = config.baseUrl().endsWith("/")
                ? config.baseUrl() + "chat/completions"
                : config.baseUrl() + "/chat/completions";

        int attempt = 0;
        long backoffMs = 1000;
        while (true) {
            attempt++;
            try {
                HttpRequest httpReq = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + config.apiKey())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .timeout(Duration.ofSeconds(config.timeout()))
                        .build();

                HttpResponse<InputStream> response = http.send(httpReq, HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();

                if (isRetryable(status) && attempt <= config.maxRetries()) {
                    long waitMs = backoffMs;
                    if (status == 429) {
                        String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
                        if (retryAfter != null) {
                            try { waitMs = Long.parseLong(retryAfter.trim()) * 1000; } catch (NumberFormatException ignored) {}
                        }
                    }
                    response.body().close();
                    sleep(waitMs);
                    backoffMs = Math.min(backoffMs * 2, 30_000);
                    continue;
                }

                if (status != 200) {
                    String errorBody = new String(response.body().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

                    response.body().close();
                    throw new RuntimeException("OpenAI API error " + status + ": " + errorBody);
                }

                // Stream started — no more retries from here
                processStream(response.body(), callback);
                return;

            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                if (attempt <= config.maxRetries()) {
                    sleep(backoffMs);
                    backoffMs = Math.min(backoffMs * 2, 30_000);
                    continue;
                }
                callback.onError(e instanceof IOException ? (IOException) e : new IOException(e));
                return;
            }
        }
    }

    private void processStream(InputStream in, LlmStreamCallback callback) {
        StringBuilder textBuffer = new StringBuilder();
        Map<Integer, ToolCallAccumulator> toolCallAccumulators = new LinkedHashMap<>();
        LlmUsage[] usageHolder = new LlmUsage[1];
        String[] finishReason = new String[1];
        boolean[] errorCalled = {false};

        try (in) {
            SseParser.parse(in, event -> {
                String data = event.data();
                if ("[DONE]".equals(data)) {
                    return;
                }

                try {
                    Map<String, Object> chunk = JsonReader.parseObject(data);
                    List<Object> choices = JsonReader.getList(chunk, "choices");
                    if (choices.isEmpty()) {
                        // Usage-only chunk (stream_options include_usage)
                        Map<String, Object> usageMap = JsonReader.getMap(chunk, "usage");
                        if (!usageMap.isEmpty()) {
                            usageHolder[0] = parseUsageFromMap(usageMap);
                        }
                        return;
                    }

                    @SuppressWarnings("unchecked")
                    Map<String, Object> choice = (Map<String, Object>) choices.get(0);
                    String fr = JsonReader.getString(choice, "finish_reason");
                    if (fr != null) {
                        finishReason[0] = fr;
                    }

                    @SuppressWarnings("unchecked")
                    Map<String, Object> delta = (Map<String, Object>) choice.get("delta");
                    if (delta == null) return;

                    // Text content delta
                    String content = (String) delta.get("content");
                    if (content != null && !content.isEmpty()) {
                        textBuffer.append(content);
                        callback.onToken(content);
                    }

                    // Tool call deltas
                    List<Object> toolCallsDelta = (List<Object>) delta.get("tool_calls");
                    if (toolCallsDelta != null) {
                        for (Object tcObj : toolCallsDelta) {
                            if (!(tcObj instanceof Map<?, ?> tc)) continue;
                            @SuppressWarnings("unchecked")
                            Map<String, Object> tcMap = (Map<String, Object>) tc;
                            int index = ((Number) tcMap.get("index")).intValue();

                            ToolCallAccumulator acc = toolCallAccumulators.computeIfAbsent(
                                    index, i -> new ToolCallAccumulator());
                            String id = JsonReader.getString(tcMap, "id");
                            if (id != null) acc.callId = id;

                            @SuppressWarnings("unchecked")
                            Map<String, Object> fn = (Map<String, Object>) tcMap.get("function");
                            if (fn != null) {
                                String name = JsonReader.getString(fn, "name");
                                if (name != null) acc.toolName = name;
                                String args = JsonReader.getString(fn, "arguments");
                                if (args != null) acc.arguments.append(args);
                            }
                        }
                    }

                    // Usage in streaming chunk
                    Map<String, Object> usageMap = JsonReader.getMap(chunk, "usage");
                    if (!usageMap.isEmpty()) {
                        usageHolder[0] = parseUsageFromMap(usageMap);
                    }
                } catch (Exception e) {
                    if (!errorCalled[0]) {
                        errorCalled[0] = true;
                        callback.onError(e);
                    }
                }
            });
        } catch (IOException e) {
            if (!errorCalled[0]) {
                callback.onError(e);
            }
            return;
        }

        // Build final response
        if (!toolCallAccumulators.isEmpty()) {
            List<ToolCall> calls = new ArrayList<>();
            for (ToolCallAccumulator acc : toolCallAccumulators.values()) {
                Map<String, Object> args = Map.of();
                String argsStr = acc.arguments.toString();
                if (!argsStr.isEmpty()) {
                    try { args = JsonReader.parseObject(argsStr); } catch (Exception ignored) {}
                }
                calls.add(new ToolCall(acc.toolName, args, acc.callId));
            }
            callback.onComplete(new LlmResponse.ToolCallResponse(
                    calls, usageHolder[0], finishReason[0] != null ? finishReason[0] : "tool_calls"));
        } else {
            callback.onComplete(new LlmResponse.TextResponse(
                    textBuffer.toString(), usageHolder[0], finishReason[0] != null ? finishReason[0] : "stop"));
        }
    }

    // --- request serialization ---

    private String buildRequestBody(LlmRequest req, boolean stream) {
        JsonWriter body = JsonWriter.object()
                .field("model", config.model())
                .field("temperature", req.temperature())
                .field("max_tokens", req.maxTokens());

        if (stream) {
            body.field("stream", true);
        }

        body.arrayField("messages", buildMessages(req));

        if (!req.tools().isEmpty()) {
            body.arrayField("tools", buildTools(req.tools()));
        }

        return body.build();
    }

    private List<String> buildMessages(LlmRequest req) {
        List<String> out = new ArrayList<>();

        if (req.systemPrompt() != null && !req.systemPrompt().isBlank()) {
            out.add(JsonWriter.object().field("role", "system").field("content", req.systemPrompt()).build());
        }

        for (Message msg : req.messages()) {
            out.add(serializeMessage(msg));
        }
        return out;
    }

    private String serializeMessage(Message msg) {
        if (msg instanceof ToolMessage tm) {
            JsonWriter w = JsonWriter.object()
                    .field("role", "tool")
                    .field("content", tm.content());
            String callId = tm.toolCallId() != null ? tm.toolCallId() : tm.toolName();
            w.field("tool_call_id", callId);
            return w.build();
        }
        return JsonWriter.object()
                .field("role", msg.role())
                .field("content", msg.content())
                .build();
    }

    private List<String> buildTools(List<ToolSchema> schemas) {
        List<String> out = new ArrayList<>();
        for (ToolSchema schema : schemas) {
            String fn = JsonWriter.object()
                    .field("name", schema.name())
                    .field("description", schema.description() != null ? schema.description() : "")
                    .rawField("parameters", JsonWriter.fromMap(schema.parameters()))
                    .build();
            out.add(JsonWriter.object()
                    .field("type", "function")
                    .rawField("function", fn)
                    .build());
        }
        return out;
    }

    // --- response parsing ---

    private LlmResponse parseResponse(String json) {
        Map<String, Object> root = JsonReader.parseObject(json);

        LlmUsage usage = parseUsage(root);
        String finishReason = JsonReader.getString(root, "choices.0.finish_reason");

        // check for tool calls
        List<Object> toolCallsRaw = JsonReader.getList(root, "choices.0.message.tool_calls");
        if (!toolCallsRaw.isEmpty()) {
            List<ToolCall> calls = new ArrayList<>();
            for (Object item : toolCallsRaw) {
                if (item instanceof Map<?, ?> tc) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> toolCallMap = (Map<String, Object>) tc;
                    String callId = JsonReader.getString(toolCallMap, "id");
                    String name = JsonReader.getString(toolCallMap, "function.name");
                    String argsJson = JsonReader.getString(toolCallMap, "function.arguments");
                    Map<String, Object> args = (argsJson != null && !argsJson.isBlank())
                            ? JsonReader.parseObject(argsJson) : Map.of();
                    calls.add(new ToolCall(name, args, callId));
                }
            }
            return new LlmResponse.ToolCallResponse(calls, usage, finishReason != null ? finishReason : "tool_calls");
        }

        String content = JsonReader.getString(root, "choices.0.message.content");
        return new LlmResponse.TextResponse(
                content != null ? content : "",
                usage,
                finishReason != null ? finishReason : "stop");
    }

    private LlmUsage parseUsage(Map<String, Object> root) {
        long prompt = JsonReader.getLong(root, "usage.prompt_tokens");
        long completion = JsonReader.getLong(root, "usage.completion_tokens");
        long total = JsonReader.getLong(root, "usage.total_tokens");
        if (prompt == 0 && completion == 0 && total == 0) return null;
        return new LlmUsage((int) prompt, (int) completion, (int) total);
    }

    private static LlmUsage parseUsageFromMap(Map<String, Object> usageMap) {
        long prompt = JsonReader.getLong(usageMap, "prompt_tokens");
        long completion = JsonReader.getLong(usageMap, "completion_tokens");
        long total = JsonReader.getLong(usageMap, "total_tokens");
        if (prompt == 0 && completion == 0 && total == 0) return null;
        return new LlmUsage((int) prompt, (int) completion, (int) total);
    }

    // --- helpers ---

    private static boolean isRetryable(int status) {
        return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Accumulator for streaming tool call deltas. */
    private static class ToolCallAccumulator {
        String callId;
        String toolName;
        StringBuilder arguments = new StringBuilder();
    }
}
