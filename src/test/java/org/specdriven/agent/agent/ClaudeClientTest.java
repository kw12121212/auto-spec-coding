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

class ClaudeClientTest {

    private static HttpServer server;
    private static int port;
    private static ExecutorService executor;
    private static final AtomicReference<HttpHandler> handlerRef = new AtomicReference<>();

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.createContext("/v1/messages", exchange -> handlerRef.get().handle(exchange));
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
        return new LlmConfig("http://localhost:" + port + "/v1", "sk-ant-test", "claude-sonnet-4-6", 10, maxRetries);
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

    private static String textResponse(String text) {
        return "{\"content\":[{\"type\":\"text\",\"text\":\"" + text + "\"}],"
                + "\"stop_reason\":\"end_turn\","
                + "\"usage\":{\"input_tokens\":10,\"output_tokens\":5}}";
    }

    private static String toolUseResponse(String callId, String toolName, String inputJson) {
        return "{\"content\":[{\"type\":\"tool_use\",\"id\":\"" + callId
                + "\",\"name\":\"" + toolName + "\",\"input\":" + inputJson + "}],"
                + "\"stop_reason\":\"tool_use\","
                + "\"usage\":{\"input_tokens\":8,\"output_tokens\":12}}";
    }

    @Test
    void textResponseParsed() {
        respond(200, textResponse("hello world"));
        LlmResponse resp = new ClaudeClient(config(0)).chat(List.of(new UserMessage("hi", 0)));

        assertInstanceOf(LlmResponse.TextResponse.class, resp);
        assertEquals("hello world", ((LlmResponse.TextResponse) resp).content());
    }

    @Test
    void toolCallResponseParsed() {
        respond(200, toolUseResponse("tool_abc", "bash", "{\"command\":\"ls\"}"));
        LlmResponse resp = new ClaudeClient(config(0)).chat(List.of(new UserMessage("run ls", 0)));

        assertInstanceOf(LlmResponse.ToolCallResponse.class, resp);
        LlmResponse.ToolCallResponse tcr = (LlmResponse.ToolCallResponse) resp;
        assertEquals(1, tcr.toolCalls().size());
        ToolCall call = tcr.toolCalls().get(0);
        assertEquals("bash", call.toolName());
        assertEquals("tool_abc", call.callId());
        assertEquals("ls", call.parameters().get("command"));
    }

    @Test
    void usageParsed() {
        respond(200, textResponse("ok"));
        LlmResponse resp = new ClaudeClient(config(0)).chat(List.of(new UserMessage("hi", 0)));

        LlmUsage usage = ((LlmResponse.TextResponse) resp).usage();
        assertNotNull(usage);
        assertEquals(10, usage.promptTokens());
        assertEquals(5, usage.completionTokens());
        assertEquals(15, usage.totalTokens());
    }

    @Test
    void systemPromptAsTopLevelField() {
        AtomicReference<String> captured = new AtomicReference<>();
        handlerRef.set(exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeJson(exchange, 200, textResponse("ok"));
        });

        LlmRequest req = new LlmRequest(List.of(new UserMessage("hi", 0)), "You are helpful.", null, 0.7, 100, null);
        new ClaudeClient(config(0)).chat(req);

        Map<String, Object> parsed = JsonReader.parseObject(captured.get());
        assertEquals("You are helpful.", parsed.get("system"));
        List<Object> messages = JsonReader.getList(parsed, "messages");
        for (Object item : messages) {
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = (Map<String, Object>) item;
            assertNotEquals("system", msg.get("role"),
                    "system prompt must not appear in messages array");
        }
    }

    @Test
    void toolSchemaUsesInputSchemaNoPyTypeWrapper() {
        AtomicReference<String> captured = new AtomicReference<>();
        handlerRef.set(exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeJson(exchange, 200, textResponse("ok"));
        });

        ToolSchema schema = new ToolSchema("bash", "Run bash", Map.of("type", "object"));
        LlmRequest req = new LlmRequest(List.of(new UserMessage("go", 0)), null, List.of(schema), 0.7, 100, null);
        new ClaudeClient(config(0)).chat(req);

        Map<String, Object> parsed = JsonReader.parseObject(captured.get());
        List<Object> tools = JsonReader.getList(parsed, "tools");
        assertEquals(1, tools.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> tool = (Map<String, Object>) tools.get(0);
        assertNull(tool.get("type"));
        assertEquals("bash", tool.get("name"));
        assertNotNull(tool.get("input_schema"));
        assertNull(tool.get("parameters"));
    }

    @Test
    void toolMessageSerializedAsUserRoleWithToolResult() {
        AtomicReference<String> captured = new AtomicReference<>();
        handlerRef.set(exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeJson(exchange, 200, textResponse("ok"));
        });

        ToolMessage toolMsg = new ToolMessage("file.txt", 0, "bash", "tool_xyz");
        LlmRequest req = new LlmRequest(List.of(new UserMessage("go", 0), toolMsg), null, null, 0.7, 100, null);
        new ClaudeClient(config(0)).chat(req);

        Map<String, Object> parsed = JsonReader.parseObject(captured.get());
        List<Object> messages = JsonReader.getList(parsed, "messages");
        boolean found = false;
        for (Object item : messages) {
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = (Map<String, Object>) item;
            if ("user".equals(msg.get("role")) && msg.get("content") instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> contentList = (List<Object>) msg.get("content");
                for (Object block : contentList) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> b = (Map<String, Object>) block;
                    if ("tool_result".equals(b.get("type"))) {
                        assertEquals("tool_xyz", b.get("tool_use_id"));
                        assertEquals("file.txt", b.get("content"));
                        found = true;
                    }
                }
            }
        }
        assertTrue(found, "Expected a user message with tool_result content block");
    }

    @Test
    void maxTokensDefaultsTo4096() {
        AtomicReference<String> captured = new AtomicReference<>();
        handlerRef.set(exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeJson(exchange, 200, textResponse("ok"));
        });

        new ClaudeClient(config(0)).chat(List.of(new UserMessage("hi", 0)));
        Map<String, Object> parsed = JsonReader.parseObject(captured.get());
        assertEquals(4096, JsonReader.getLong(parsed, "max_tokens"));
    }

    @Test
    void requiredHeadersPresent() {
        AtomicReference<HttpExchange> capturedExchange = new AtomicReference<>();
        handlerRef.set(exchange -> {
            capturedExchange.set(exchange);
            writeJson(exchange, 200, textResponse("ok"));
        });

        new ClaudeClient(config(0)).chat(List.of(new UserMessage("hi", 0)));

        var headers = capturedExchange.get().getRequestHeaders();
        assertNotNull(headers.getFirst("x-api-key"));
        assertEquals("sk-ant-test", headers.getFirst("x-api-key"));
        assertNotNull(headers.getFirst("anthropic-version"));
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

        LlmResponse resp = new ClaudeClient(config(1)).chat(List.of(new UserMessage("hi", 0)));
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

        LlmResponse resp = new ClaudeClient(new LlmConfig("http://localhost:" + port + "/v1", "key", "claude-sonnet-4-6", 10, 3))
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

        LlmClient client = new ClaudeClient(config(3));
        assertThrows(RuntimeException.class, () -> client.chat(List.of(new UserMessage("hi", 0))));
        assertEquals(1, callCount.get());
    }

    @Test
    void exhaustedRetriesThrowsException() {
        handlerRef.set(exchange -> {
            exchange.sendResponseHeaders(503, 0);
            exchange.getResponseBody().close();
        });

        LlmClient client = new ClaudeClient(config(2));
        assertThrows(RuntimeException.class, () -> client.chat(List.of(new UserMessage("hi", 0))));
    }
}
