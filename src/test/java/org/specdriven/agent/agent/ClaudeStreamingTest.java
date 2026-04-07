package org.specdriven.agent.agent;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ClaudeStreamingTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.setExecutor(Executors.newSingleThreadExecutor());
        port = server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private LlmConfig config() {
        return new LlmConfig("http://localhost:" + port + "/v1", "sk-ant-test", "claude-3-opus", 10, 0);
    }

    private void streamSSE(String... eventPairs) {
        // eventPairs: alternating event type and data
        server.createContext("/v1/messages", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                for (int i = 0; i < eventPairs.length; i += 2) {
                    os.write(("event: " + eventPairs[i] + "\ndata: " + eventPairs[i + 1] + "\n\n").getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
            }
        });
    }

    private static String messageStart(int inputTokens) {
        return "{\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"type\":\"message\","
                + "\"role\":\"assistant\",\"content\":[],\"model\":\"claude-3-opus\","
                + "\"usage\":{\"input_tokens\":" + inputTokens + ",\"output_tokens\":0}}}";
    }

    private static String textBlockStart() {
        return "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}";
    }

    private static String textDelta(String text) {
        return "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"" + text + "\"}}";
    }

    private static String contentBlockStop(int index) {
        return "{\"type\":\"content_block_stop\",\"index\":" + index + "}";
    }

    private static String toolUseStart(int index, String id, String name) {
        return "{\"type\":\"content_block_start\",\"index\":" + index
                + ",\"content_block\":{\"type\":\"tool_use\",\"id\":\"" + id + "\",\"name\":\"" + name + "\"}}";
    }

    private static String toolUseDelta(int index, String partialJson) {
        return "{\"type\":\"content_block_delta\",\"index\":" + index
                + ",\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"" + partialJson + "\"}}";
    }

    private static String messageDelta(String stopReason, int outputTokens) {
        return "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"" + stopReason
                + "\"},\"usage\":{\"output_tokens\":" + outputTokens + "}}";
    }

    private static final String MESSAGE_STOP = "{\"type\":\"message_stop\"}";

    // --- tests ---

    @Test
    void streamTextTokens() {
        streamSSE(
                "message_start", messageStart(25),
                "content_block_start", textBlockStart(),
                "content_block_delta", textDelta("Hello"),
                "content_block_delta", textDelta(" world"),
                "content_block_stop", contentBlockStop(0),
                "message_delta", messageDelta("end_turn", 5),
                "message_stop", MESSAGE_STOP
        );

        StringBuilder tokens = new StringBuilder();
        AtomicReference<LlmResponse> response = new AtomicReference<>();
        LlmStreamCallback callback = new LlmStreamCallback() {
            @Override public void onToken(String token) { tokens.append(token); }
            @Override public void onComplete(LlmResponse r) { response.set(r); }
            @Override public void onError(Exception e) { throw new RuntimeException(e); }
        };

        new ClaudeClient(config()).chatStreaming(LlmRequest.of(List.of(new UserMessage("hi", 0))), callback);

        assertEquals("Hello world", tokens.toString());
        assertInstanceOf(LlmResponse.TextResponse.class, response.get());
        assertEquals("Hello world", ((LlmResponse.TextResponse) response.get()).content());
        assertEquals("end_turn", ((LlmResponse.TextResponse) response.get()).finishReason());
    }

    @Test
    void streamToolCallDeltas() {
        streamSSE(
                "message_start", messageStart(20),
                "content_block_start", toolUseStart(0, "toolu_123", "bash"),
                "content_block_delta", toolUseDelta(0, "{\\\"command\\\":\\\"ls\\\"}"),
                "content_block_stop", contentBlockStop(0),
                "message_delta", messageDelta("tool_use", 10),
                "message_stop", MESSAGE_STOP
        );

        AtomicReference<LlmResponse> response = new AtomicReference<>();
        LlmStreamCallback callback = new LlmStreamCallback() {
            @Override public void onToken(String token) {}
            @Override public void onComplete(LlmResponse r) { response.set(r); }
            @Override public void onError(Exception e) { throw new RuntimeException(e); }
        };

        new ClaudeClient(config()).chatStreaming(LlmRequest.of(List.of(new UserMessage("run ls", 0))), callback);

        assertInstanceOf(LlmResponse.ToolCallResponse.class, response.get());
        LlmResponse.ToolCallResponse tcr = (LlmResponse.ToolCallResponse) response.get();
        assertEquals(1, tcr.toolCalls().size());
        assertEquals("bash", tcr.toolCalls().get(0).toolName());
        assertEquals("toolu_123", tcr.toolCalls().get(0).callId());
        assertEquals("ls", tcr.toolCalls().get(0).parameters().get("command"));
    }

    @Test
    void streamMessageStopTerminates() {
        streamSSE(
                "message_start", messageStart(10),
                "content_block_start", textBlockStart(),
                "content_block_delta", textDelta("hi"),
                "content_block_stop", contentBlockStop(0),
                "message_delta", messageDelta("end_turn", 2),
                "message_stop", MESSAGE_STOP
        );

        AtomicBoolean completed = new AtomicBoolean(false);
        LlmStreamCallback callback = new LlmStreamCallback() {
            @Override public void onToken(String token) {}
            @Override public void onComplete(LlmResponse r) { completed.set(true); }
            @Override public void onError(Exception e) { throw new RuntimeException(e); }
        };

        new ClaudeClient(config()).chatStreaming(LlmRequest.of(List.of(new UserMessage("hi", 0))), callback);
        assertTrue(completed.get());
    }

    @Test
    void streamUsageFromMessageStartAndDelta() {
        streamSSE(
                "message_start", messageStart(100),
                "content_block_start", textBlockStart(),
                "content_block_delta", textDelta("ok"),
                "content_block_stop", contentBlockStop(0),
                "message_delta", messageDelta("end_turn", 15),
                "message_stop", MESSAGE_STOP
        );

        AtomicReference<LlmResponse> response = new AtomicReference<>();
        LlmStreamCallback callback = new LlmStreamCallback() {
            @Override public void onToken(String token) {}
            @Override public void onComplete(LlmResponse r) { response.set(r); }
            @Override public void onError(Exception e) { throw new RuntimeException(e); }
        };

        new ClaudeClient(config()).chatStreaming(LlmRequest.of(List.of(new UserMessage("hi", 0))), callback);

        LlmUsage usage = ((LlmResponse.TextResponse) response.get()).usage();
        assertNotNull(usage);
        assertEquals(100, usage.promptTokens());
        assertEquals(15, usage.completionTokens());
        assertEquals(115, usage.totalTokens());
    }

    @Test
    void streamRequestIncludesStreamTrue() {
        AtomicReference<String> captured = new AtomicReference<>();
        server.createContext("/v1/messages", exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            OutputStream os = exchange.getResponseBody();
            os.write(("event: message_stop\ndata: " + MESSAGE_STOP + "\n\n").getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();
        });

        AtomicBoolean completed = new AtomicBoolean(false);
        LlmStreamCallback callback = new LlmStreamCallback() {
            @Override public void onToken(String token) {}
            @Override public void onComplete(LlmResponse r) { completed.set(true); }
            @Override public void onError(Exception e) { throw new RuntimeException(e); }
        };

        new ClaudeClient(config()).chatStreaming(LlmRequest.of(List.of(new UserMessage("hi", 0))), callback);

        String body = captured.get();
        assertNotNull(body);
        assertTrue(body.contains("\"stream\":true"));
    }

    @Test
    void streamRetryOnPreStream429() {
        AtomicInteger callCount = new AtomicInteger(0);
        server.createContext("/v1/messages", exchange -> {
            int c = callCount.incrementAndGet();
            if (c == 1) {
                exchange.getResponseHeaders().set("Retry-After", "0");
                exchange.sendResponseHeaders(429, 0);
                exchange.getResponseBody().close();
            } else {
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, 0);
                OutputStream os = exchange.getResponseBody();
                os.write(("event: message_start\ndata: " + messageStart(10) + "\n\n").getBytes(StandardCharsets.UTF_8));
                os.write(("event: message_stop\ndata: " + MESSAGE_STOP + "\n\n").getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.close();
            }
        });

        LlmConfig cfg = new LlmConfig("http://localhost:" + port + "/v1", "sk-ant-test", "claude-3-opus", 10, 1);
        AtomicBoolean completed = new AtomicBoolean(false);
        LlmStreamCallback callback = new LlmStreamCallback() {
            @Override public void onToken(String token) {}
            @Override public void onComplete(LlmResponse r) { completed.set(true); }
            @Override public void onError(Exception e) { throw new RuntimeException(e); }
        };

        new ClaudeClient(cfg).chatStreaming(LlmRequest.of(List.of(new UserMessage("hi", 0))), callback);
        assertEquals(2, callCount.get());
        assertTrue(completed.get());
    }
}
