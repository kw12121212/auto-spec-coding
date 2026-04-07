package org.specdriven.agent.agent;

import org.specdriven.agent.json.JsonReader;
import org.specdriven.agent.json.JsonWriter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
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
        String body = buildRequestBody(request);
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

    // --- request serialization ---

    private String buildRequestBody(LlmRequest req) {
        int maxTokens = req.maxTokens() > 0 ? req.maxTokens() : 4096;

        JsonWriter body = JsonWriter.object()
                .field("model", config.model())
                .field("max_tokens", maxTokens)
                .field("temperature", req.temperature());

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
}
