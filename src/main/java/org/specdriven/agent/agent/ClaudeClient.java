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
 * LlmClient implementation for the Anthropic Claude Messages API.
 */
public class ClaudeClient implements LlmClient {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final LlmConfig config;
    private final HttpClient http;

    ClaudeClient(LlmConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.timeout()))
                .build();
    }

    @Override
    public LlmResponse chat(List<Message> messages) {
        return chat(LlmRequest.of(messages));
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        String body = buildRequestBody(request, false);
        String url = config.baseUrl().endsWith("/")
                ? config.baseUrl() + "messages"
                : config.baseUrl() + "/messages";

        int attempt = 0;
        long backoffMs = 1000;
        while (true) {
            attempt++;
            try {
                HttpRequest httpReq = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("x-api-key", config.apiKey())
                        .header("anthropic-version", ANTHROPIC_VERSION)
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

                throw new RuntimeException("Claude API error " + status + ": " + response.body());

            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                if (attempt <= config.maxRetries()) {
                    sleep(backoffMs);
                    backoffMs = Math.min(backoffMs * 2, 30_000);
                    continue;
                }
                throw new RuntimeException("Claude request failed after " + attempt + " attempts", e);
            }
        }
    }

    @Override
    public void chatStreaming(LlmRequest request, LlmStreamCallback callback) {
        String body = buildRequestBody(request, true);
        String url = config.baseUrl().endsWith("/")
                ? config.baseUrl() + "messages"
                : config.baseUrl() + "/messages";

        int attempt = 0;
        long backoffMs = 1000;
        while (true) {
            attempt++;
            try {
                HttpRequest httpReq = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("x-api-key", config.apiKey())
                        .header("anthropic-version", ANTHROPIC_VERSION)
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
                    String errorBody = new String(response.body().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);                    response.body().close();
                    throw new RuntimeException("Claude API error " + status + ": " + errorBody);
                }

                // Stream started — no more retries
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
        int[] inputTokens = {0};
        int[] outputTokens = {0};
        String[] stopReason = {null};
        boolean[] errorCalled = {false};

        try (in) {
            SseParser.parse(in, event -> {
                try {
                    Map<String, Object> data = JsonReader.parseObject(event.data());
                    String type = JsonReader.getString(data, "type");

                    switch (type) {
                        case "message_start" -> {
                            Map<String, Object> message = JsonReader.getMap(data, "message");
                            Map<String, Object> usage = JsonReader.getMap(message, "usage");
                            inputTokens[0] = (int) JsonReader.getLong(usage, "input_tokens");
                        }
                        case "content_block_start" -> {
                            Map<String, Object> contentBlock = JsonReader.getMap(data, "content_block");
                            int index = ((Number) data.get("index")).intValue();
                            String blockType = JsonReader.getString(contentBlock, "type");
                            if ("tool_use".equals(blockType)) {
                                ToolCallAccumulator acc = new ToolCallAccumulator();
                                acc.callId = JsonReader.getString(contentBlock, "id");
                                acc.toolName = JsonReader.getString(contentBlock, "name");
                                toolCallAccumulators.put(index, acc);
                            }
                        }
                        case "content_block_delta" -> {
                            int index = ((Number) data.get("index")).intValue();
                            Map<String, Object> delta = JsonReader.getMap(data, "delta");
                            String deltaType = JsonReader.getString(delta, "type");

                            if ("text_delta".equals(deltaType)) {
                                String text = JsonReader.getString(delta, "text");
                                if (text != null && !text.isEmpty()) {
                                    textBuffer.append(text);
                                    callback.onToken(text);
                                }
                            } else if ("input_json_delta".equals(deltaType)) {
                                String partialJson = JsonReader.getString(delta, "partial_json");
                                if (partialJson != null) {
                                    ToolCallAccumulator acc = toolCallAccumulators.get(index);
                                    if (acc != null) {
                                        acc.arguments.append(partialJson);
                                    }
                                }
                            }
                        }
                        case "message_delta" -> {
                            Map<String, Object> delta = JsonReader.getMap(data, "delta");
                            String sr = JsonReader.getString(delta, "stop_reason");
                            if (sr != null) stopReason[0] = sr;
                            Map<String, Object> usage = JsonReader.getMap(data, "usage");
                            outputTokens[0] = (int) JsonReader.getLong(usage, "output_tokens");
                        }
                        case "error" -> {
                            String message = JsonReader.getString(data, "error.message");
                            callback.onError(new RuntimeException(
                                    message != null ? message : event.data()));
                            errorCalled[0] = true;
                        }
                        // message_stop, content_block_stop — no action needed
                    }
                } catch (Exception e) {
                    if (!errorCalled[0]) {
                        callback.onError(e);
                        errorCalled[0] = true;
                    }
                }
            });
        } catch (IOException e) {
            if (!errorCalled[0]) {
                callback.onError(e);
            }
            return;
        }

        if (errorCalled[0]) return;

        // Build usage
        LlmUsage usage = null;
        if (inputTokens[0] > 0 || outputTokens[0] > 0) {
            usage = new LlmUsage(inputTokens[0], outputTokens[0], inputTokens[0] + outputTokens[0]);
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
                    calls, usage, stopReason[0] != null ? stopReason[0] : "tool_use"));
        } else {
            callback.onComplete(new LlmResponse.TextResponse(
                    textBuffer.toString(), usage, stopReason[0] != null ? stopReason[0] : "end_turn"));
        }
    }

    // --- request serialization ---

    private String buildRequestBody(LlmRequest req, boolean stream) {
        int maxTokens = req.maxTokens() > 0 ? req.maxTokens() : 4096;

        JsonWriter body = JsonWriter.object()
                .field("model", config.model())
                .field("max_tokens", maxTokens)
                .field("temperature", req.temperature());

        if (stream) {
            body.field("stream", true);
        }

        if (req.systemPrompt() != null && !req.systemPrompt().isBlank()) {
            body.field("system", req.systemPrompt());
        }

        body.arrayField("messages", buildMessages(req.messages()));

        if (!req.tools().isEmpty()) {
            body.arrayField("tools", buildTools(req.tools()));
        }

        return body.build();
    }

    private List<String> buildMessages(List<Message> messages) {
        List<String> out = new ArrayList<>();
        for (Message msg : messages) {
            out.add(serializeMessage(msg));
        }
        return out;
    }

    private String serializeMessage(Message msg) {
        if (msg instanceof ToolMessage tm) {
            // Claude requires tool results as user messages with tool_result content blocks
            String toolUseId = tm.toolCallId() != null ? tm.toolCallId() : tm.toolName();
            String toolResultBlock = JsonWriter.object()
                    .field("type", "tool_result")
                    .field("tool_use_id", toolUseId)
                    .field("content", tm.content())
                    .build();
            String contentArray = "[" + toolResultBlock + "]";
            return JsonWriter.object()
                    .field("role", "user")
                    .rawField("content", contentArray)
                    .build();
        }
        // UserMessage, AssistantMessage, SystemMessage — serialize as role/content pair
        return JsonWriter.object()
                .field("role", msg.role())
                .field("content", msg.content())
                .build();
    }

    private List<String> buildTools(List<ToolSchema> schemas) {
        List<String> out = new ArrayList<>();
        for (ToolSchema schema : schemas) {
            out.add(JsonWriter.object()
                    .field("name", schema.name())
                    .field("description", schema.description() != null ? schema.description() : "")
                    .rawField("input_schema", JsonWriter.fromMap(schema.parameters()))
                    .build());
        }
        return out;
    }

    // --- response parsing ---

    private LlmResponse parseResponse(String json) {
        Map<String, Object> root = JsonReader.parseObject(json);

        LlmUsage usage = parseUsage(root);
        String stopReason = JsonReader.getString(root, "stop_reason");

        List<Object> contentBlocks = JsonReader.getList(root, "content");

        // collect tool_use blocks
        List<ToolCall> toolCalls = new ArrayList<>();
        StringBuilder textBuffer = new StringBuilder();

        for (Object item : contentBlocks) {
            if (!(item instanceof Map<?, ?> block)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> b = (Map<String, Object>) block;
            String type = JsonReader.getString(b, "type");

            if ("tool_use".equals(type)) {
                String callId = JsonReader.getString(b, "id");
                String name = JsonReader.getString(b, "name");
                Map<String, Object> input = JsonReader.getMap(b, "input");
                toolCalls.add(new ToolCall(name, input, callId));
            } else if ("text".equals(type)) {
                String text = JsonReader.getString(b, "text");
                if (text != null) textBuffer.append(text);
            }
        }

        if (!toolCalls.isEmpty()) {
            return new LlmResponse.ToolCallResponse(toolCalls, usage, stopReason != null ? stopReason : "tool_use");
        }

        return new LlmResponse.TextResponse(
                textBuffer.toString(),
                usage,
                stopReason != null ? stopReason : "end_turn");
    }

    private LlmUsage parseUsage(Map<String, Object> root) {
        long input = JsonReader.getLong(root, "usage.input_tokens");
        long output = JsonReader.getLong(root, "usage.output_tokens");
        if (input == 0 && output == 0) return null;
        return new LlmUsage((int) input, (int) output, (int) (input + output));
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
