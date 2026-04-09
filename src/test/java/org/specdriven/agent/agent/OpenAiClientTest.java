package org.specdriven.agent.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.specdriven.agent.json.JsonReader;

class OpenAiClientTest {

    private static HttpServer server;
    private static int port;
    private static ExecutorService executor;
    private static final AtomicReference<HttpHandler> handlerRef = new AtomicReference<>();

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.createContext("/v1/chat/completions", exchange -> handlerRef.get().handle(exchange));
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
        executor.shutdownNow();
    }

    @BeforeEach
    void resetHandler() {
        respond(200, textResponse("ok"));
    }

    private LlmConfig config(int maxRetries) {
        return new LlmConfig("http://localhost:" + port + "/v1", "sk-test", "gpt-4", 10, maxRetries);
    }

    private void respond(int status, String body) {
        handlerRef.set(exchange -> writeJson(exchange, status, body));
    }

    private static void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
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
        handlerRef.set(exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeJson(exchange, 200, textResponse("ok"));
        });

        LlmClient client = new OpenAiClient(config(0));
        LlmRequest req = new LlmRequest(List.of(new UserMessage("hello", 0)), "You are helpful.", null, 0.7, 100, null);
        client.chat(req);

        assertNotNull(captured.get());
        Map<String, Object> parsed = JsonReader.parseObject(captured.get());
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
        handlerRef.set(exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeJson(exchange, 200, textResponse("ok"));
        });

        ToolSchema schema = new ToolSchema("bash", "Run a bash command", Map.of("type", "object"));
        LlmRequest req = new LlmRequest(List.of(new UserMessage("go", 0)), null, List.of(schema), 0.7, 100, null);
        new OpenAiClient(config(0)).chat(req);

        Map<String, Object> parsed = JsonReader.parseObject(captured.get());
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
        handlerRef.set(exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeJson(exchange, 200, textResponse("ok"));
        });

        ToolMessage toolMsg = new ToolMessage("result", 0, "bash", "call_xyz");
        LlmRequest req = new LlmRequest(List.of(new UserMessage("go", 0), toolMsg), null, null, 0.7, 100, null);
        new OpenAiClient(config(0)).chat(req);

        Map<String, Object> parsed = JsonReader.parseObject(captured.get());
        List<Object> messages = JsonReader.getList(parsed, "messages");
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
        AtomicReference<HttpExchange> captured = new AtomicReference<>();
        handlerRef.set(exchange -> {
            captured.set(exchange);
            writeJson(exchange, 200, textResponse("ok"));
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
        handlerRef.set(exchange -> {
            int c = callCount.incrementAndGet();
            if (c == 1) {
                exchange.getResponseHeaders().set("Retry-After", "0");
                exchange.sendResponseHeaders(429, 0);
                exchange.getResponseBody().close();
            } else {
                writeJson(exchange, 200, textResponse("ok"));
            }
        });

        LlmResponse resp = new OpenAiClient(config(1)).chat(List.of(new UserMessage("hi", 0)));
        assertEquals(2, callCount.get());
        assertInstanceOf(LlmResponse.TextResponse.class, resp);
    }

    @Test
    void retryOn500WithExponentialBackoff() {
        AtomicInteger callCount = new AtomicInteger(0);
        handlerRef.set(exchange -> {
            int c = callCount.incrementAndGet();
            if (c < 3) {
                exchange.sendResponseHeaders(500, 0);
                exchange.getResponseBody().close();
            } else {
                writeJson(exchange, 200, textResponse("recovered"));
            }
        });

        LlmResponse resp = new OpenAiClient(new LlmConfig("http://localhost:" + port + "/v1", "key", "gpt-4", 10, 3))
                .chat(List.of(new UserMessage("hi", 0)));
        assertEquals(3, callCount.get());
        assertEquals("recovered", ((LlmResponse.TextResponse) resp).content());
    }

    @Test
    void noRetryOn401() {
        AtomicInteger callCount = new AtomicInteger(0);
        handlerRef.set(exchange -> {
            callCount.incrementAndGet();
            writeJson(exchange, 401, "{\"error\":\"unauthorized\"}");
        });

        LlmClient client = new OpenAiClient(config(3));
        assertThrows(RuntimeException.class, () -> client.chat(List.of(new UserMessage("hi", 0))));
        assertEquals(1, callCount.get());
    }

    @Test
    void exhaustedRetriesThrowsException() {
        handlerRef.set(exchange -> {
            exchange.sendResponseHeaders(503, 0);
            exchange.getResponseBody().close();
        });

        LlmClient client = new OpenAiClient(config(2));
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> client.chat(List.of(new UserMessage("hi", 0))));
        assertTrue(ex.getMessage().contains("503"));
    }

    @Test
    void customBaseUrlRoutesToCorrectEndpoint() {
        AtomicReference<String> path = new AtomicReference<>();
        handlerRef.set(exchange -> {
            path.set(exchange.getRequestURI().getPath());
            writeJson(exchange, 200, textResponse("ok"));
        });

        new OpenAiClient(new LlmConfig("http://localhost:" + port + "/v1", "key", "gpt-4", 10, 0))
                .chat(List.of(new UserMessage("hi", 0)));

        assertEquals("/v1/chat/completions", path.get());
    }
}
