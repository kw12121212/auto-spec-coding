package org.specdriven.agent.http;

import org.junit.jupiter.api.*;
import org.specdriven.agent.tool.Tool;
import org.specdriven.agent.tool.ToolContext;
import org.specdriven.agent.tool.ToolInput;
import org.specdriven.agent.tool.ToolParameter;
import org.specdriven.agent.tool.ToolResult;
import org.specdriven.sdk.SpecDriven;

import java.net.http.HttpResponse;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests that exercise the full HTTP REST API stack:
 * HTTP request → AuthFilter → RateLimitFilter → HttpApiServlet → SDK → JSON response.
 */
class HttpE2eTest {

    private static final String API_KEY = "test-api-key";

    private static HttpTestStack stack;

    @BeforeAll
    static void startServer() throws Exception {
        stack = new HttpTestStack(API_KEY, 100, 60, testSdk()).start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (stack != null) {
            stack.close();
        }
    }

    // --- Helpers ---

    private HttpResponse<String> get(String path) throws Exception {
        return stack.get(path);
    }

    private HttpResponse<String> getWithAuth(String path) throws Exception {
        return stack.getWithBearer(path);
    }

    private HttpResponse<String> postWithAuth(String path, String body) throws Exception {
        return stack.postWithBearer(path, body);
    }

    private HttpResponse<String> postWithAuthNoBody(String path) throws Exception {
        return stack.postWithBearerNoBody(path);
    }

    private String jsonField(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) return null;
        start += needle.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    private String createAgent() throws Exception {
        HttpResponse<String> resp = postWithAuth("/agent/run", "{\"prompt\":\"test\"}");
        assertEquals(200, resp.statusCode(), "createAgent should succeed");
        return jsonField(resp.body(), "agentId");
    }

    private static SpecDriven testSdk() {
        return SpecDriven.builder()
                .registerTool(new StubTool("bash", "run commands",
                        List.of(new ToolParameter("command", "string", "the command", true))))
                .build();
    }

    // --- Test stack fixture ---

    @Test
    @Timeout(10)
    void testStackStartsAndStopsCleanly() throws Exception {
        int releasedPort;
        try (HttpTestStack localStack = new HttpTestStack(API_KEY, 5, 60, testSdk()).start()) {
            HttpResponse<String> response = localStack.get("/health");
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"status\":\"ok\""));
            releasedPort = localStack.port();
        }

        assertPortReleased(releasedPort);
    }

    private static void assertPortReleased(int port) throws Exception {
        AssertionError lastError = null;
        for (int attempt = 0; attempt < 10; attempt++) {
            try (ServerSocket socket = new ServerSocket()) {
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress("localhost", port));
                return;
            } catch (Exception e) {
                lastError = new AssertionError("Port " + port + " was not released", e);
                Thread.sleep(100);
            }
        }
        throw lastError;
    }

    // --- Health endpoint ---

    @Test
    @Timeout(10)
    void health_returns200WithOkStatus() throws Exception {
        HttpResponse<String> resp = get("/health");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"status\":\"ok\""));
        assertTrue(resp.body().contains("\"version\":\"0.1.0\""));
    }

    @Test
    @Timeout(10)
    void health_noAuthRequired() throws Exception {
        HttpResponse<String> resp = get("/health");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.headers().firstValue("Content-Type").orElse("").contains("application/json"));
    }

    // --- Tools list ---

    @Test
    @Timeout(10)
    void toolsList_withAuth_returns200() throws Exception {
        HttpResponse<String> resp = getWithAuth("/tools");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"tools\":["));
        assertTrue(resp.body().contains("\"name\":\"bash\""));
        assertTrue(resp.body().contains("\"command\""));
    }

    // --- Agent run ---

    @Test
    @Timeout(10)
    void agentRun_validRequest_returns200() throws Exception {
        HttpResponse<String> resp = postWithAuth("/agent/run", "{\"prompt\":\"hello\"}");
        assertEquals(200, resp.statusCode());
        assertNotNull(jsonField(resp.body(), "agentId"));
        assertTrue(resp.body().contains("\"output\":"));
        assertTrue(resp.body().contains("\"state\":\"STOPPED\""));
    }

    @Test
    @Timeout(10)
    void agentRun_missingPrompt_returns400() throws Exception {
        HttpResponse<String> resp = postWithAuth("/agent/run", "{\"systemPrompt\":\"be helpful\"}");
        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("\"error\":\"invalid_params\""));
    }

    @Test
    @Timeout(10)
    void agentRun_emptyBody_returns400() throws Exception {
        HttpResponse<String> resp = postWithAuth("/agent/run", "");
        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("\"error\":\"invalid_params\""));
    }

    // --- Agent state ---

    @Test
    @Timeout(10)
    void agentState_validId_returns200() throws Exception {
        String agentId = createAgent();
        HttpResponse<String> resp = getWithAuth("/agent/state?id=" + agentId);
        assertEquals(200, resp.statusCode());
        assertEquals(agentId, jsonField(resp.body(), "agentId"));
        assertTrue(resp.body().contains("\"createdAt\":"));
        assertTrue(resp.body().contains("\"updatedAt\":"));
    }

    @Test
    @Timeout(10)
    void agentState_unknownId_returns404() throws Exception {
        HttpResponse<String> resp = getWithAuth("/agent/state?id=nonexistent-id");
        assertEquals(404, resp.statusCode());
        assertTrue(resp.body().contains("\"error\":\"not_found\""));
    }

    @Test
    @Timeout(10)
    void agentState_missingId_returns400() throws Exception {
        HttpResponse<String> resp = getWithAuth("/agent/state");
        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("\"error\":\"invalid_params\""));
    }

    // --- Agent stop ---

    @Test
    @Timeout(10)
    void agentStop_validId_returns200() throws Exception {
        String agentId = createAgent();
        HttpResponse<String> resp = postWithAuthNoBody("/agent/stop?id=" + agentId);
        assertEquals(200, resp.statusCode());
    }

    @Test
    @Timeout(10)
    void agentStop_unknownId_returns404() throws Exception {
        HttpResponse<String> resp = postWithAuthNoBody("/agent/stop?id=nonexistent-id");
        assertEquals(404, resp.statusCode());
    }

    @Test
    @Timeout(10)
    void agentStop_missingId_returns400() throws Exception {
        HttpResponse<String> resp = postWithAuthNoBody("/agent/stop");
        assertEquals(400, resp.statusCode());
    }

    // --- Auth filter ---

    @Test
    @Timeout(10)
    void auth_validBearerToken_passes() throws Exception {
        HttpResponse<String> resp = getWithAuth("/tools");
        assertEquals(200, resp.statusCode());
    }

    @Test
    @Timeout(10)
    void auth_validXApiKey_passes() throws Exception {
        HttpResponse<String> resp = stack.getWithApiKey("/tools");
        assertEquals(200, resp.statusCode());
    }

    @Test
    @Timeout(10)
    void auth_missingAuth_returns401() throws Exception {
        HttpResponse<String> resp = get("/tools");
        assertEquals(401, resp.statusCode());
        assertTrue(resp.body().contains("\"error\":\"unauthorized\""));
    }

    @Test
    @Timeout(10)
    void auth_invalidKey_returns401() throws Exception {
        HttpResponse<String> resp = stack.getWithBearerToken("/tools", "wrong-key");
        assertEquals(401, resp.statusCode());
    }

    @Test
    @Timeout(10)
    void auth_healthBypassesAuth() throws Exception {
        HttpResponse<String> resp = get("/health");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"status\":\"ok\""));
    }

    // --- Rate limiting ---

    @Nested
    class RateLimitTests {

        private static final int LOW_MAX = 3;
        private static final int WINDOW_SEC = 2;

        private HttpTestStack rateLimitStack;
        private AtomicLong fakeClock;

        @BeforeEach
        void startRlServer() throws Exception {
            fakeClock = new AtomicLong(System.currentTimeMillis());
            rateLimitStack = new HttpTestStack(API_KEY, LOW_MAX, WINDOW_SEC, testSdk(),
                    fakeClock::get).start();
        }

        @AfterEach
        void stopRlServer() throws Exception {
            if (rateLimitStack != null) {
                rateLimitStack.close();
            }
        }

        private HttpResponse<String> rlGet(String path) throws Exception {
            return rateLimitStack.getWithBearer(path);
        }

        @Test
        @Timeout(10)
        void underLimit_passes() throws Exception {
            for (int i = 0; i < LOW_MAX; i++) {
                HttpResponse<String> resp = rlGet("/tools");
                assertEquals(200, resp.statusCode(), "Request " + (i + 1) + " should pass");
            }
        }

        @Test
        @Timeout(10)
        void overLimit_returns429() throws Exception {
            for (int i = 0; i < LOW_MAX; i++) {
                rlGet("/tools");
            }
            HttpResponse<String> resp = rlGet("/tools");
            assertEquals(429, resp.statusCode());
            assertTrue(resp.body().contains("\"error\":\"rate_limited\""));
            String retryAfter = resp.headers().firstValue("Retry-After").orElse(null);
            assertNotNull(retryAfter, "Should have Retry-After header");
            assertTrue(Integer.parseInt(retryAfter) >= 1);
        }

        @Test
        @Timeout(10)
        void windowExpiry_allowsNewRequests() throws Exception {
            for (int i = 0; i < LOW_MAX; i++) {
                rlGet("/tools");
            }
            // Advance the fake clock past the rate-limit window — no wall-clock sleep needed
            fakeClock.addAndGet((WINDOW_SEC + 1) * 1000L);
            HttpResponse<String> resp = rlGet("/tools");
            assertEquals(200, resp.statusCode());
        }
    }

    // --- Error paths ---

    @Test
    @Timeout(10)
    void unknownRoute_returns404() throws Exception {
        HttpResponse<String> resp = getWithAuth("/nonexistent");
        assertEquals(404, resp.statusCode());
        assertTrue(resp.body().contains("\"error\":\"not_found\""));
    }

    @Test
    @Timeout(10)
    void wrongMethod_returns405() throws Exception {
        HttpResponse<String> resp = postWithAuth("/health", "{}");
        assertEquals(405, resp.statusCode());
        assertTrue(resp.body().contains("\"error\":\"method_not_allowed\""));
    }

    // --- Agent lifecycle ---

    @Test
    @Timeout(10)
    void agentLifecycle_run_state_stop_state() throws Exception {
        // Step 1: Run agent
        HttpResponse<String> runResp = postWithAuth("/agent/run", "{\"prompt\":\"lifecycle test\"}");
        assertEquals(200, runResp.statusCode());
        String agentId = jsonField(runResp.body(), "agentId");
        assertNotNull(agentId);

        // Step 2: Query state
        HttpResponse<String> stateResp1 = getWithAuth("/agent/state?id=" + agentId);
        assertEquals(200, stateResp1.statusCode());
        assertEquals(agentId, jsonField(stateResp1.body(), "agentId"));
        assertEquals("STOPPED", jsonField(stateResp1.body(), "state"));

        // Step 3: Stop agent
        HttpResponse<String> stopResp = postWithAuthNoBody("/agent/stop?id=" + agentId);
        assertEquals(200, stopResp.statusCode());

        // Step 4: Query state again
        HttpResponse<String> stateResp2 = getWithAuth("/agent/state?id=" + agentId);
        assertEquals(200, stateResp2.statusCode());
        assertEquals("STOPPED", jsonField(stateResp2.body(), "state"));
    }

    // --- Stub Tool ---

    private record StubTool(String name, String description, List<ToolParameter> parameters) implements Tool {
        @Override
        public String getName() { return name; }
        @Override
        public String getDescription() { return description; }
        @Override
        public List<ToolParameter> getParameters() { return parameters != null ? parameters : List.of(); }
        @Override
        public ToolResult execute(ToolInput input, ToolContext context) {
            return new ToolResult.Success("ok");
        }
    }
}
