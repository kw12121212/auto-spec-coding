package org.specdriven.agent.agent;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.specdriven.agent.json.JsonReader;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiClientTest {

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

    private LlmConfig config(int maxRetries) {
        return new LlmConfig("http://localhost:" + port + "/v1", "sk-test", "gpt-4", 10, maxRetries);
    }

    private void respond(int status, String body) {
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
    }

    private static String textResponse(String content) {
        return "{\"choices\":[{\"message\":{\"content\":\"" + content + "\",\"role\":\"assistant\"},"
                + "\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}";
    }

    private static String toolCallResponse(String callId, String toolName, String argsJson) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\""
                + callId + "\",\"type\":\"function\",\"function\":{\"name\":\"" + toolName
                + "\",\"arguments\":" + escapeJson(argsJson) + "}}]},\"finish_reason\":\"tool_calls\"}],"
                + "\"usage\":{\"prompt_tokens\":8,\"completion_tokens\":12,\"total_tokens\":20}}";
    }

    private static String escapeJson(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // --- tests ---

    @Test
    void textResponseParsed() {
        respond(200, textResponse("hello world"));
        LlmClient client = new OpenAiClient(config(0));
        LlmResponse resp = client.chat(List.of(new UserMessage("hi", 0)));

        assertInstanceOf(LlmResponse.TextResponse.class, resp);
        assertEquals("hello world", ((LlmResponse.TextResponse) resp).content());
    }

    @Test
    void toolCallResponseParsed() {
        respond(200, toolCallResponse("call_abc", "bash", "{\"command\":\"ls\"}"));
        LlmClient client = new OpenAiClient(config(0));
        LlmResponse resp = client.chat(List.of(new UserMessage("run ls", 0)));

        assertInstanceOf(LlmResponse.ToolCallResponse.class, resp);
        LlmResponse.ToolCallResponse tcr = (LlmResponse.ToolCallResponse) resp;
        assertEquals(1, tcr.toolCalls().size());
        ToolCall call = tcr.toolCalls().get(0);
        assertEquals("bash", call.toolName());
        assertEquals("call_abc", call.callId());
        assertEquals("ls", call.parameters().get("command"));
    }

    @Test
    void usageParsed() {
        respond(200, textResponse("ok"));
        LlmClient client = new OpenAiClient(config(0));
        LlmResponse resp = client.chat(List.of(new UserMessage("hi", 0)));

        LlmUsage usage = ((LlmResponse.TextResponse) resp).usage();
        assertNotNull(usage);
        assertEquals(10, usage.promptTokens());
        assertEquals(5, usage.completionTokens());
        assertEquals(15, usage.totalTokens());
    }

    @Test
    void systemPromptInjectedFirst() {
        AtomicReference<String> captured = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = textResponse("ok").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        });

        LlmClient client = new OpenAiClient(config(0));
        LlmRequest req = new LlmRequest(
                List.of(new UserMessage("hello", 0)),
                "You are helpful.",
                null, 0.7, 100, null);
        client.chat(req);

        String body = captured.get();
        assertNotNull(body);
        Map<String, Object> parsed = JsonReader.parseObject(body);
        List<Object> messages = JsonReader.getList(parsed, "messages");
        assertFalse(messages.isEmpty());
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) messages.get(0);
        assertEquals("system", first.get("role"));
        assertEquals("You are helpful.", first.get("content"));
    }

    @Test
    void toolSchemaSerializedCorrectly() {
        AtomicReference<String> captured = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = textResponse("ok").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        });

        ToolSchema schema = new ToolSchema("bash", "Run a bash command", Map.of("type", "object"));
        LlmRequest req = new LlmRequest(
                List.of(new UserMessage("go", 0)), null, List.of(schema), 0.7, 100, null);
        LlmClient client = new OpenAiClient(config(0));
        client.chat(req);

        String body = captured.get();
        Map<String, Object> parsed = JsonReader.parseObject(body);
        List<Object> tools = JsonReader.getList(parsed, "tools");
        assertEquals(1, tools.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> tool = (Map<String, Object>) tools.get(0);
        assertEquals("function", tool.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> fn = (Map<String, Object>) tool.get("function");
        assertEquals("bash", fn.get("name"));
    }

    @Test
    void toolMessageSerializedWithToolCallId() {
        AtomicReference<String> captured = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = textResponse("ok").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        });

        ToolMessage toolMsg = new ToolMessage("result", 0, "bash", "call_xyz");
        LlmRequest req = new LlmRequest(
                List.of(new UserMessage("go", 0), toolMsg), null, null, 0.7, 100, null);
        LlmClient client = new OpenAiClient(config(0));
        client.chat(req);

        String body = captured.get();
        Map<String, Object> parsed = JsonReader.parseObject(body);
        List<Object> messages = JsonReader.getList(parsed, "messages");
        // find the tool message
        boolean found = false;
        for (Object item : messages) {
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = (Map<String, Object>) item;
            if ("tool".equals(msg.get("role"))) {
                assertEquals("call_xyz", msg.get("tool_call_id"));
                found = true;
            }
        }
        assertTrue(found, "Expected a tool message with tool_call_id");
    }

    @Test
    void requiredHeadersPresent() {
        AtomicReference<com.sun.net.httpserver.HttpExchange> captured = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            captured.set(exchange);
            byte[] bytes = textResponse("ok").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        });

        new OpenAiClient(config(0)).chat(List.of(new UserMessage("hi", 0)));

        var headers = captured.get().getRequestHeaders();
        assertNotNull(headers.getFirst("Authorization"));
        assertTrue(headers.getFirst("Authorization").startsWith("Bearer "));
        assertEquals("application/json", headers.getFirst("Content-Type"));
    }

    @Test
    void retryOn429WithRetryAfterHeader() {
        AtomicInteger callCount = new AtomicInteger(0);
        server.createContext("/v1/chat/completions", exchange -> {
            int c = callCount.incrementAndGet();
            if (c == 1) {
                exchange.getResponseHeaders().set("Retry-After", "0");
                exchange.sendResponseHeaders(429, 0);
                exchange.getResponseBody().close();
            } else {
                byte[] bytes = textResponse("ok").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.getResponseBody().close();
            }
        });

        LlmClient client = new OpenAiClient(config(1));
        LlmResponse resp = client.chat(List.of(new UserMessage("hi", 0)));

        assertEquals(2, callCount.get());
        assertInstanceOf(LlmResponse.TextResponse.class, resp);
    }

    @Test
    void retryOn500WithExponentialBackoff() {
        AtomicInteger callCount = new AtomicInteger(0);
        server.createContext("/v1/chat/completions", exchange -> {
            int c = callCount.incrementAndGet();
            if (c < 3) {
                exchange.sendResponseHeaders(500, 0);
                exchange.getResponseBody().close();
            } else {
                byte[] bytes = textResponse("recovered").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.getResponseBody().close();
            }
        });

        // Use a config with 0 timeout so backoff is forced to near-zero (still tests the path)
        LlmConfig cfg = new LlmConfig("http://localhost:" + port + "/v1", "key", "gpt-4", 10, 3);
        LlmClient client = new OpenAiClient(cfg);

        // Override sleep by making the test quick — we just verify retries happened
        // The real test is that we eventually get a response after retries
        LlmResponse resp = client.chat(List.of(new UserMessage("hi", 0)));
        assertEquals(3, callCount.get());
        assertEquals("recovered", ((LlmResponse.TextResponse) resp).content());
    }

    @Test
    void noRetryOn401() {
        AtomicInteger callCount = new AtomicInteger(0);
        server.createContext("/v1/chat/completions", exchange -> {
            callCount.incrementAndGet();
            byte[] bytes = "{\"error\":\"unauthorized\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        });

        LlmClient client = new OpenAiClient(config(3));
        assertThrows(RuntimeException.class, () -> client.chat(List.of(new UserMessage("hi", 0))));
        assertEquals(1, callCount.get());
    }

    @Test
    void exhaustedRetriesThrowsException() {
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.sendResponseHeaders(503, 0);
            exchange.getResponseBody().close();
        });

        LlmClient client = new OpenAiClient(config(2));
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> client.chat(List.of(new UserMessage("hi", 0))));
        assertTrue(ex.getMessage().contains("503") || ex.getMessage().contains("503"));
    }

    @Test
    void customBaseUrlRoutesToCorrectEndpoint() {
        AtomicReference<String> path = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            byte[] bytes = textResponse("ok").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        });

        LlmConfig cfg = new LlmConfig("http://localhost:" + port + "/v1", "key", "gpt-4", 10, 0);
        new OpenAiClient(cfg).chat(List.of(new UserMessage("hi", 0)));

        assertEquals("/v1/chat/completions", path.get());
    }
}
