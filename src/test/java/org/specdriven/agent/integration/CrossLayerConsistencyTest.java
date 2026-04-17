package org.specdriven.agent.integration;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.specdriven.agent.agent.*;
import org.specdriven.agent.http.AuthFilter;
import org.specdriven.agent.http.HttpApiServlet;
import org.specdriven.agent.json.JsonReader;
import org.specdriven.agent.jsonrpc.JsonRpcDispatcher;
import org.specdriven.agent.jsonrpc.StdioTransport;
import org.specdriven.agent.permission.Permission;
import org.specdriven.agent.tool.Tool;
import org.specdriven.agent.tool.ToolContext;
import org.specdriven.agent.tool.ToolInput;
import org.specdriven.agent.tool.ToolParameter;
import org.specdriven.agent.tool.ToolResult;
import org.specdriven.sdk.SdkAgent;
import org.specdriven.sdk.SpecDriven;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-layer consistency tests verifying that the three interface layers
 * (Native Java SDK, JSON-RPC over stdin, HTTP REST API) produce consistent
 * observable outcomes for the same logical operations.
 *
 * <p>Note: The JSON-RPC layer creates its own internal SDK on {@code initialize},
 * so agent/run tests focus on SDK + HTTP parity. JSON-RPC parity checks focus on
 * shared logical outcomes and explicit divergence boundaries rather than exact
 * response-envelope equality with HTTP.</p>
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class CrossLayerConsistencyTest {

    private static final String API_KEY = "test-integ-key";
    private static final String STUB_TOOL_NAME = "stub-echo";
    private static final String STUB_TOOL_DESC = "echoes input back";

    // --- Shared fixtures ---

    private static StubLlmProvider stubProvider;
    private static SpecDriven sdk;
    private static AtomicInteger toolInvocationCount;

    // --- HTTP harness ---

    private static Tomcat tomcat;
    private static int httpPort;
    private static HttpClient httpClient;

    // --- Lifecycle ---

    @BeforeAll
    static void setUp() throws Exception {
        toolInvocationCount = new AtomicInteger(0);
        stubProvider = new StubLlmProvider();

        sdk = SpecDriven.builder()
                .providerRegistry(stubProvider.registry())
                .registerTool(new EchoTool(STUB_TOOL_NAME, STUB_TOOL_DESC,
                        List.of(new ToolParameter("text", "string", "text to echo", true)),
                        toolInvocationCount))
                .systemPrompt("You are a test assistant.")
                .build();

        // Start embedded Tomcat
        tomcat = new Tomcat();
        tomcat.setBaseDir(System.getProperty("java.io.tmpdir") + "/tomcat-cross-" + UUID.randomUUID());
        tomcat.setPort(0);

        Context ctx = tomcat.addContext("", new File(".").getCanonicalPath());
        addFilter(ctx, "authFilter", new AuthFilter(), "/api/v1/*", Map.of("API_KEYS", API_KEY));
        Tomcat.addServlet(ctx, "apiServlet", new HttpApiServlet(sdk));
        ctx.addServletMappingDecoded("/api/v1/*", "apiServlet");

        tomcat.start();
        httpPort = tomcat.getConnector().getLocalPort();

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (tomcat != null) {
            tomcat.stop();
            tomcat.destroy();
        }
        if (sdk != null) {
            sdk.close();
        }
    }

    // ========================================================================
    // Test: Happy path agent run (SDK + HTTP)
    // ========================================================================

    @Test
    @Timeout(15)
    void happyPath_sdkAndHttp_bothReturnStoppedState() throws Exception {
        stubProvider.resetResponses(
                new LlmResponse.TextResponse("Hello from stub!")
        );

        // SDK layer
        SdkAgent sdkAgent = sdk.createAgent();
        String sdkOutput = sdkAgent.run("hello");
        assertEquals("Hello from stub!", sdkOutput);
        assertEquals(AgentState.STOPPED, sdkAgent.getState());

        // HTTP layer
        stubProvider.resetResponses(
                new LlmResponse.TextResponse("Hello from stub!")
        );
        HttpResponse<String> resp = postWithAuth("/agent/run", "{\"prompt\":\"hello\"}");
        assertEquals(200, resp.statusCode());
        Map<String, Object> httpResult = JsonReader.parseObject(resp.body());
        assertEquals("STOPPED", httpResult.get("state"));
        assertEquals(sdkOutput, httpResult.get("output"));
    }

    // ========================================================================
    // Test: Tool call round-trip (SDK + HTTP)
    // ========================================================================

    @Test
    @Timeout(15)
    void toolCallRoundTrip_sdkAndHttp_bothInvokeToolAndReturnText() throws Exception {
        toolInvocationCount.set(0);
        stubProvider.resetResponses(
                new LlmResponse.ToolCallResponse(List.of(
                        new ToolCall(STUB_TOOL_NAME, Map.of("text", "ping"), "call-1")
                )),
                new LlmResponse.TextResponse("Tool said: echo-ping")
        );

        // SDK layer
        SdkAgent sdkAgent = sdk.createAgent();
        String sdkOutput = sdkAgent.run("use the tool");
        assertEquals("Tool said: echo-ping", sdkOutput);
        assertEquals(AgentState.STOPPED, sdkAgent.getState());
        int sdkInvocations = toolInvocationCount.get();
        assertTrue(sdkInvocations >= 1, "Stub tool should have been invoked at least once via SDK");

        // HTTP layer
        toolInvocationCount.set(0);
        stubProvider.resetResponses(
                new LlmResponse.ToolCallResponse(List.of(
                        new ToolCall(STUB_TOOL_NAME, Map.of("text", "ping"), "call-1")
                )),
                new LlmResponse.TextResponse("Tool said: echo-ping")
        );
        HttpResponse<String> resp = postWithAuth("/agent/run", "{\"prompt\":\"use the tool\"}");
        assertEquals(200, resp.statusCode());
        Map<String, Object> httpResult = JsonReader.parseObject(resp.body());
        assertEquals("STOPPED", httpResult.get("state"));
        assertEquals(sdkOutput, httpResult.get("output"));
        int httpInvocations = toolInvocationCount.get();
        assertTrue(httpInvocations >= 1, "Stub tool should have been invoked via HTTP");
    }

    // ========================================================================
    // Test: Agent state query (SDK + HTTP)
    // ========================================================================

    @Test
    @Timeout(15)
    void agentStateQuery_sdkAndHttp_bothReturnState() throws Exception {
        stubProvider.resetResponses(
                new LlmResponse.TextResponse("done")
        );

        // SDK layer
        SdkAgent sdkAgent = sdk.createAgent();
        sdkAgent.run("go");
        assertEquals(AgentState.STOPPED, sdkAgent.getState());

        // HTTP layer
        HttpResponse<String> runResp = postWithAuth("/agent/run", "{\"prompt\":\"go\"}");
        assertEquals(200, runResp.statusCode());
        String agentId = jsonField(runResp.body(), "agentId");
        assertNotNull(agentId);

        HttpResponse<String> stateResp = getWithAuth("/agent/state?id=" + agentId);
        assertEquals(200, stateResp.statusCode());
        assertEquals(agentId, jsonField(stateResp.body(), "agentId"));
        assertEquals("STOPPED", jsonField(stateResp.body(), "state"));
    }

    // ========================================================================
    // Test: Tools list parity (SDK + HTTP)
    // ========================================================================

    @Test
    @Timeout(10)
    void toolsList_sdkAndHttp_sameToolNamesAndParameters() throws Exception {
        // SDK layer
        List<Tool> sdkTools = sdk.tools();
        assertEquals(1, sdkTools.size());
        Tool sdkTool = sdkTools.getFirst();

        // HTTP layer
        HttpResponse<String> resp = getWithAuth("/tools");
        assertEquals(200, resp.statusCode());
        Map<String, Object> httpResult = JsonReader.parseObject(resp.body());
        List<Map<String, Object>> httpTools = mapList(httpResult.get("tools"));
        assertEquals(1, httpTools.size());
        Map<String, Object> httpTool = httpTools.getFirst();
        assertEquals(sdkTool.getName(), httpTool.get("name"));
        assertEquals(sdkTool.getDescription(), httpTool.get("description"));

        List<Map<String, Object>> httpParameters = mapList(httpTool.get("parameters"));
        assertEquals(sdkTool.getParameters().size(), httpParameters.size());
        Map<String, Object> httpParameter = httpParameters.getFirst();
        ToolParameter sdkParameter = sdkTool.getParameters().getFirst();
        assertEquals(sdkParameter.name(), httpParameter.get("name"));
        assertEquals(sdkParameter.type(), httpParameter.get("type"));
        assertEquals(sdkParameter.description(), httpParameter.get("description"));
        assertEquals(sdkParameter.required(), httpParameter.get("required"));
    }

    @Test
    @Timeout(10)
    void releaseMetadata_httpHealthAndJsonRpcInitialize_shareVersion() throws Exception {
        HttpRequest healthReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/health"))
                .GET()
                .build();
        HttpResponse<String> healthResp = httpClient.send(healthReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, healthResp.statusCode());
        Map<String, Object> healthResult = JsonReader.parseObject(healthResp.body());

        String initReq = jsonRpcRequest(1, "initialize", Map.of("systemPrompt", "test"));
        List<ParsedFrame> frames = executeJsonRpc(frame(initReq), 1, 3000);
        assertEquals(1, frames.size());
        Map<String, Object> initResult = resultField(frames.getFirst());

        assertEquals(healthResult.get("version"), initResult.get("version"));
        assertEquals("0.1.0", initResult.get("version"));
    }

    // ========================================================================
    // Test: Tools list via JSON-RPC
    // ========================================================================

    @Test
    @Timeout(10)
    void toolsList_jsonRpc_returnsValidStructure() throws Exception {
        String initReq = jsonRpcRequest(1, "initialize", Map.of("systemPrompt", "test"));
        String toolsReq = jsonRpcRequest(2, "tools/list", null);
        byte[] allFrames = joinFrames(frame(initReq), frame(toolsReq));

        List<ParsedFrame> frames = executeJsonRpc(allFrames, 2, 5000);
        assertEquals(2, frames.size());

        // First response: initialize result
        Map<String, Object> initResult = resultField(frames.get(0));
        assertTrue(initResult.containsKey("version"));
        assertTrue(initResult.containsKey("capabilities"));

        // Second response: tools/list result
        Map<String, Object> toolResult = resultField(frames.get(1));
        assertTrue(toolResult.containsKey("tools"));
    }

    // ========================================================================
    // Test: Error consistency — missing prompt
    // ========================================================================

    @Test
    @Timeout(10)
    void missingPrompt_httpReturns400() throws Exception {
        HttpResponse<String> resp = postWithAuth("/agent/run", "{\"systemPrompt\":\"be helpful\"}");
        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("\"error\":\"invalid_params\""));
    }

    @Test
    @Timeout(10)
    void missingPrompt_httpEmptyBody_returns400() throws Exception {
        HttpResponse<String> resp = postWithAuth("/agent/run", "");
        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("\"error\":\"invalid_params\""));
    }

    @Test
    @Timeout(10)
    void missingPrompt_jsonRpcReturnsInvalidParams() throws Exception {
        String initReq = jsonRpcRequest(1, "initialize", Map.of("systemPrompt", "test"));
        String runReq = jsonRpcRequest(2, "agent/run", Map.of("notPrompt", "value"));
        byte[] allFrames = joinFrames(frame(initReq), frame(runReq));

        List<ParsedFrame> frames = executeJsonRpc(allFrames, 2, 5000);
        // Second frame should be an error with code -32602
        assertTrue(frames.size() >= 2);
        Map<String, Object> fields = frames.get(1).fields();
        assertTrue(fields.containsKey("error"));
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) fields.get("error");
        assertEquals(-32602, ((Number) error.get("code")).intValue());
    }

    @Test
    @Timeout(10)
    void missingPrompt_httpAndJsonRpc_bothRejectInvalidInputExplicitly() throws Exception {
        HttpResponse<String> httpResp = postWithAuth("/agent/run", "{\"systemPrompt\":\"be helpful\"}");
        assertEquals(400, httpResp.statusCode());
        Map<String, Object> httpError = JsonReader.parseObject(httpResp.body());
        assertEquals("invalid_params", httpError.get("error"));

        String initReq = jsonRpcRequest(1, "initialize", Map.of("systemPrompt", "test"));
        String runReq = jsonRpcRequest(2, "agent/run", Map.of("notPrompt", "value"));
        List<ParsedFrame> frames = executeJsonRpc(joinFrames(frame(initReq), frame(runReq)), 2, 5000);
        assertEquals(2, frames.size());
        Map<String, Object> rpcError = errorField(frames.get(1));
        assertEquals(-32602, ((Number) rpcError.get("code")).intValue());
    }

    // ========================================================================
    // Test: HTTP 401 unauthorized
    // ========================================================================

    @Test
    @Timeout(10)
    void unauthorized_missingAuth_returns401() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/tools"))
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, resp.statusCode());
        assertTrue(resp.body().contains("\"error\":\"unauthorized\""));
    }

    @Test
    @Timeout(10)
    void unauthorized_invalidKey_returns401() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/tools"))
                .header("Authorization", "Bearer wrong-key")
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, resp.statusCode());
    }

    @Test
    @Timeout(10)
    void unauthorized_healthBypassesAuth() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/health"))
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"status\":\"ok\""));
    }

    // ========================================================================
    // Test: JSON-RPC protocol
    // ========================================================================

    @Test
    @Timeout(10)
    void jsonRpc_initialize_returnsVersionAndCapabilities() throws Exception {
        String initReq = jsonRpcRequest(1, "initialize", Map.of("systemPrompt", "test"));
        List<ParsedFrame> frames = executeJsonRpc(frame(initReq), 1, 3000);
        assertEquals(1, frames.size());

        Map<String, Object> result = resultField(frames.get(0));
        assertEquals("0.1.0", result.get("version"));

        @SuppressWarnings("unchecked")
        Map<String, Object> caps = (Map<String, Object>) result.get("capabilities");
        assertNotNull(caps);
        assertTrue(((List<?>) caps.get("methods")).contains("agent/run"));
    }

    @Test
    @Timeout(10)
    void jsonRpc_unknownMethod_returnsMethodNotFound() throws Exception {
        String initReq = jsonRpcRequest(1, "initialize", Map.of("systemPrompt", "test"));
        String badReq = jsonRpcRequest(2, "nonexistent/method", null);
        byte[] allFrames = joinFrames(frame(initReq), frame(badReq));

        List<ParsedFrame> frames = executeJsonRpc(allFrames, 2, 5000);
        assertTrue(frames.size() >= 2);
        Map<String, Object> fields = frames.get(1).fields();
        assertTrue(fields.containsKey("error"));
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) fields.get("error");
        assertEquals(-32601, ((Number) error.get("code")).intValue());
    }

    @Test
    @Timeout(10)
    void unsupportedOperation_httpAndJsonRpc_bothRejectExplicitly() throws Exception {
        HttpResponse<String> httpResp = getWithAuth("/nonexistent");
        assertEquals(404, httpResp.statusCode());
        Map<String, Object> httpError = JsonReader.parseObject(httpResp.body());
        assertEquals("not_found", httpError.get("error"));

        String initReq = jsonRpcRequest(1, "initialize", Map.of("systemPrompt", "test"));
        String badReq = jsonRpcRequest(2, "nonexistent/method", null);
        List<ParsedFrame> frames = executeJsonRpc(joinFrames(frame(initReq), frame(badReq)), 2, 5000);
        assertEquals(2, frames.size());
        Map<String, Object> rpcError = errorField(frames.get(1));
        assertEquals(-32601, ((Number) rpcError.get("code")).intValue());
    }

    @Test
    @Timeout(10)
    void stopContract_httpAndJsonRpc_preserveSurfaceSpecificBehavior() throws Exception {
        HttpResponse<String> httpResp = postWithAuth("/agent/stop", "");
        assertEquals(400, httpResp.statusCode());
        Map<String, Object> httpError = JsonReader.parseObject(httpResp.body());
        assertEquals("invalid_params", httpError.get("error"));

        String initReq = jsonRpcRequest(1, "initialize", Map.of("systemPrompt", "test"));
        String stopReq = jsonRpcRequest(2, "agent/stop", null);
        List<ParsedFrame> frames = executeJsonRpc(joinFrames(frame(initReq), frame(stopReq)), 2, 5000);
        assertEquals(2, frames.size());
        assertFalse(frames.get(1).fields().containsKey("error"));
        assertNull(frames.get(1).fields().get("result"));
    }

    // ========================================================================
    // HTTP helpers
    // ========================================================================

    private String baseUrl() {
        return "http://localhost:" + httpPort + "/api/v1";
    }

    private HttpResponse<String> getWithAuth(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Authorization", "Bearer " + API_KEY)
                .GET()
                .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postWithAuth(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) (List<?>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> errorField(ParsedFrame frame) {
        Object error = frame.fields().get("error");
        return error instanceof Map ? (Map<String, Object>) error : Map.of();
    }

    private static String jsonField(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) return null;
        start += needle.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    // ========================================================================
    // JSON-RPC harness — all frames must be provided up front
    // ========================================================================

    /**
     * Sends pre-framed JSON-RPC requests through a fresh StdioTransport,
     * waits for the specified number of response frames, and returns them.
     */
    private static List<ParsedFrame> executeJsonRpc(byte[] framedInput, int expectedFrames, long timeoutMs)
            throws InterruptedException {
        ByteArrayInputStream input = new ByteArrayInputStream(framedInput);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        StdioTransport transport = new StdioTransport(input, output);
        JsonRpcDispatcher dispatcher = new JsonRpcDispatcher(transport);
        transport.start(dispatcher);

        // Wait for responses to appear in output
        long deadline = System.nanoTime() + timeoutMs * 1_000_000;
        List<ParsedFrame> frames;
        while (true) {
            frames = parseFrames(output.toString(StandardCharsets.UTF_8));
            if (frames.size() >= expectedFrames) {
                break;
            }
            if (System.nanoTime() >= deadline) {
                break;
            }
            Thread.sleep(50);
        }

        transport.stop();
        return frames;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resultField(ParsedFrame frame) {
        Object result = frame.fields().get("result");
        return result instanceof Map ? (Map<String, Object>) result : Map.of();
    }

    // ========================================================================
    // Frame helpers
    // ========================================================================

    private static byte[] frame(String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        String header = "Content-Length: " + body.length + "\r\n\r\n";
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[headerBytes.length + body.length];
        System.arraycopy(headerBytes, 0, result, 0, headerBytes.length);
        System.arraycopy(body, 0, result, headerBytes.length, body.length);
        return result;
    }

    private static byte[] joinFrames(byte[]... frames) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        for (byte[] f : frames) {
            try {
                buf.write(f);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return buf.toByteArray();
    }

    private static String jsonRpcRequest(Object id, String method, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"jsonrpc\":\"2.0\",\"id\":");
        if (id instanceof Number) {
            sb.append(id);
        } else {
            sb.append("\"").append(id).append("\"");
        }
        sb.append(",\"method\":\"").append(method).append("\"");
        if (params != null) {
            sb.append(",\"params\":");
            appendJsonMap(sb, params);
        } else {
            sb.append(",\"params\":null");
        }
        sb.append("}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void appendJsonMap(StringBuilder sb, Map<String, Object> map) {
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Number) {
                sb.append(v);
            } else if (v instanceof Boolean) {
                sb.append(v);
            } else if (v instanceof Map) {
                appendJsonMap(sb, (Map<String, Object>) v);
            } else {
                sb.append("\"").append(v.toString()).append("\"");
            }
            first = false;
        }
        sb.append("}");
    }

    private static List<ParsedFrame> parseFrames(String output) {
        List<ParsedFrame> frames = new ArrayList<>();
        Pattern framePattern = Pattern.compile("Content-Length:\\s*(\\d+)\\r\\n\\r\\n");
        Matcher m = framePattern.matcher(output);
        int pos = 0;
        while (m.find(pos)) {
            int contentLength = Integer.parseInt(m.group(1));
            int bodyStart = m.end();
            int bodyEnd = bodyStart + contentLength;
            if (bodyEnd > output.length()) break;
            String json = output.substring(bodyStart, bodyEnd);
            Map<String, Object> fields = JsonReader.parseObject(json);
            frames.add(new ParsedFrame(json, fields));
            pos = bodyEnd;
        }
        return frames;
    }

    record ParsedFrame(String json, Map<String, Object> fields) {}

    // ========================================================================
    // Filter registration
    // ========================================================================

    private static void addFilter(Context ctx, String name, Filter filter, String urlPattern,
                                  Map<String, String> initParams) {
        FilterDef def = new FilterDef();
        def.setFilterName(name);
        def.setFilter(filter);
        def.setFilterClass(filter.getClass().getName());
        if (initParams != null) {
            initParams.forEach(def::addInitParameter);
        }
        ctx.addFilterDef(def);
        FilterMap map = new FilterMap();
        map.setFilterName(name);
        map.addURLPatternDecoded(urlPattern);
        map.setDispatcher(DispatcherType.REQUEST.name());
        ctx.addFilterMap(map);
    }

    // ========================================================================
    // Fixtures
    // ========================================================================

    /**
     * Stub LLM provider that returns predetermined responses in sequence.
     * Thread-safe: each call to createClient() returns a client with its own response array copy.
     */
    static class StubLlmProvider implements LlmProvider {
        private volatile LlmResponse[] responses = new LlmResponse[]{
                new LlmResponse.TextResponse("stub response")
        };
        private final LlmConfig config = new LlmConfig("http://localhost:0", "stub-key", "stub-model", 10, 0);
        private final AtomicInteger cursor = new AtomicInteger(0);

        void resetResponses(LlmResponse... responses) {
            this.responses = responses;
            cursor.set(0);
        }

        @Override
        public LlmConfig config() { return config; }

        @Override
        public LlmClient createClient() {
            return new StubLlmClient(this);
        }

        @Override
        public void close() {}

        LlmProviderRegistry registry() {
            DefaultLlmProviderRegistry reg = new DefaultLlmProviderRegistry();
            reg.register("stub", this);
            return reg;
        }

        LlmResponse nextResponse() {
            LlmResponse[] current = responses;
            int index = cursor.getAndIncrement();
            if (index < current.length) {
                return current[index];
            }
            return new LlmResponse.TextResponse("stub fallback");
        }
    }

    static class StubLlmClient implements LlmClient {
        private final StubLlmProvider provider;

        StubLlmClient(StubLlmProvider provider) {
            this.provider = provider;
        }

        @Override
        public LlmResponse chat(List<Message> messages) {
            return provider.nextResponse();
        }
    }

    /**
     * Tool that echoes its "text" parameter as "echo-&lt;text&gt;".
     * Increments the shared invocation counter on each execute.
     */
    record EchoTool(String name, String description, List<ToolParameter> parameters,
                    AtomicInteger invocationCount) implements Tool {
        @Override
        public String getName() { return name; }
        @Override
        public String getDescription() { return description; }
        @Override
        public List<ToolParameter> getParameters() { return parameters != null ? parameters : List.of(); }
        @Override
        public Permission permissionFor(ToolInput input, ToolContext context) {
            // Use "read" action with workDir resource so DefaultPermissionProvider auto-allows
            return new Permission("read", context.workDir(), java.util.Map.of());
        }
        @Override
        public ToolResult execute(ToolInput input, ToolContext context) {
            invocationCount.incrementAndGet();
            Object text = input.parameters().get("text");
            return new ToolResult.Success("echo-" + text);
        }
    }
}
