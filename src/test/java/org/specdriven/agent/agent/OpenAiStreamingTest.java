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

class OpenAiStreamingTest {

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
        return new LlmConfig("http://localhost:" + port + "/v1", "sk-test", "gpt-4", 10, 0);
    }

    private void streamSSE(String... chunks) {
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                for (String chunk : chunks) {
                    os.write(("data: " + chunk + "\n\n").getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
                os.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        });
    }

    private static String textDelta(String content) {
        return "{\"choices\":[{\"delta\":{\"content\":\"" + content + "\"}}]}";
    }

    private static String toolCallStart(int index, String callId, String name) {
        return "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":" + index
                + ",\"id\":\"" + callId + "\",\"type\":\"function\",\"function\":{\"name\":\""
                + name + "\",\"arguments\":\"\"}}]}}]}";
    }

    private static String toolCallArgs(int index, String args) {
        return "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":" + index
                + ",\"function\":{\"arguments\":\"" + args + "\"}}]}}]}";
    }

    private static String finishChunk(String reason) {
        return "{\"choices\":[{\"delta\":{},\"finish_reason\":\"" + reason + "\"}]}";
    }

    private static String usageChunk(int prompt, int completion) {
        return "{\"choices\":[],\"usage\":{\"prompt_tokens\":" + prompt
                + ",\"completion_tokens\":" + completion
                + ",\"total_tokens\":" + (prompt + completion) + "}}";
    }

    // --- tests ---

    @Test
    void streamTextTokens() {
        streamSSE(textDelta("Hello"), textDelta(" world"), finishChunk("stop"));

        StringBuilder tokens = new StringBuilder();
        AtomicReference<LlmResponse> response = new AtomicReference<>();

        LlmStreamCallback callback = new LlmStreamCallback() {
            @Override public void onToken(String token) { tokens.append(token); }
            @Override public void onComplete(LlmResponse r) { response.set(r); }
            @Override public void onError(Exception e) { throw new RuntimeException(e); }
        };

        new OpenAiClient(config()).chatStreaming(LlmRequest.of(List.of(new UserMessage("hi", 0))), callback);

        assertEquals("Hello world", tokens.toString());
        assertInstanceOf(LlmResponse.TextResponse.class, response.get());
        assertEquals("Hello world", ((LlmResponse.TextResponse) response.get()).content());
        assertEquals("stop", ((LlmResponse.TextResponse) response.get()).finishReason());
    }

    @Test
    void streamToolCallDeltas() {
        streamSSE(
                toolCallStart(0, "call_abc", "bash"),
                toolCallArgs(0, "{\\\"command\\\":\\\"ls\\\"}"),
                finishChunk("tool_calls")
        );

        AtomicReference<LlmResponse> response = new AtomicReference<>();
        LlmStreamCallback callback = new LlmStreamCallback() {
            @Override public void onToken(String token) {}
            @Override public void onComplete(LlmResponse r) { response.set(r); }
            @Override public void onError(Exception e) { throw new RuntimeException(e); }
        };

        new OpenAiClient(config()).chatStreaming(LlmRequest.of(List.of(new UserMessage("run ls", 0))), callback);

        assertInstanceOf(LlmResponse.ToolCallResponse.class, response.get());
        LlmResponse.ToolCallResponse tcr = (LlmResponse.ToolCallResponse) response.get();
        assertEquals(1, tcr.toolCalls().size());
        assertEquals("bash", tcr.toolCalls().get(0).toolName());
        assertEquals("call_abc", tcr.toolCalls().get(0).callId());
        assertEquals("ls", tcr.toolCalls().get(0).parameters().get("command"));
    }

    @Test
    void streamDoneTerminates() {
        streamSSE(textDelta("hi"), finishChunk("stop"));

        AtomicBoolean completed = new AtomicBoolean(false);
        LlmStreamCallback callback = new LlmStreamCallback() {
            @Override public void onToken(String token) {}
            @Override public void onComplete(LlmResponse r) { completed.set(true); }
            @Override public void onError(Exception e) { throw new RuntimeException(e); }
        };

        new OpenAiClient(config()).chatStreaming(LlmRequest.of(List.of(new UserMessage("hi", 0))), callback);
        assertTrue(completed.get());
    }

    @Test
    void streamUsageInFinalChunk() {
        streamSSE(textDelta("ok"), finishChunk("stop"), usageChunk(10, 5));

        AtomicReference<LlmResponse> response = new AtomicReference<>();
        LlmStreamCallback callback = new LlmStreamCallback() {
            @Override public void onToken(String token) {}
            @Override public void onComplete(LlmResponse r) { response.set(r); }
            @Override public void onError(Exception e) { throw new RuntimeException(e); }
        };

        new OpenAiClient(config()).chatStreaming(LlmRequest.of(List.of(new UserMessage("hi", 0))), callback);

        LlmUsage usage = ((LlmResponse.TextResponse) response.get()).usage();
        assertNotNull(usage);
        assertEquals(10, usage.promptTokens());
        assertEquals(5, usage.completionTokens());
    }

    @Test
    void streamRequestIncludesStreamTrue() {
        AtomicReference<String> captured = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            OutputStream os = exchange.getResponseBody();
            os.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();
        });

        AtomicBoolean completed = new AtomicBoolean(false);
        LlmStreamCallback callback = new LlmStreamCallback() {
            @Override public void onToken(String token) {}
            @Override public void onComplete(LlmResponse r) { completed.set(true); }
            @Override public void onError(Exception e) { throw new RuntimeException(e); }
        };

        new OpenAiClient(config()).chatStreaming(LlmRequest.of(List.of(new UserMessage("hi", 0))), callback);
        assertTrue(completed.get());

        String body = captured.get();
        assertNotNull(body);
        assertTrue(body.contains("\"stream\":true"));
    }

    @Test
    void streamRetryOnPreStream429() {
        AtomicInteger callCount = new AtomicInteger(0);
        server.createContext("/v1/chat/completions", exchange -> {
            int c = callCount.incrementAndGet();
            if (c == 1) {
                exchange.getResponseHeaders().set("Retry-After", "0");
                exchange.sendResponseHeaders(429, 0);
                exchange.getResponseBody().close();
            } else {
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, 0);
                OutputStream os = exchange.getResponseBody();
                os.write(("data: " + textDelta("ok") + "\n\n").getBytes(StandardCharsets.UTF_8));
                os.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.close();
            }
        });

        LlmConfig cfg = new LlmConfig("http://localhost:" + port + "/v1", "sk-test", "gpt-4", 10, 1);
        AtomicBoolean completed = new AtomicBoolean(false);
        LlmStreamCallback callback = new LlmStreamCallback() {
            @Override public void onToken(String token) {}
            @Override public void onComplete(LlmResponse r) { completed.set(true); }
            @Override public void onError(Exception e) { throw new RuntimeException(e); }
        };

        new OpenAiClient(cfg).chatStreaming(LlmRequest.of(List.of(new UserMessage("hi", 0))), callback);
        assertEquals(2, callCount.get());
        assertTrue(completed.get());
    }
}
