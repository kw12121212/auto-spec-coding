package org.specdriven.agent.http;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.specdriven.agent.agent.AgentState;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.tool.Tool;
import org.specdriven.agent.tool.ToolContext;
import org.specdriven.agent.tool.ToolInput;
import org.specdriven.agent.tool.ToolParameter;
import org.specdriven.agent.tool.ToolResult;
import org.specdriven.sdk.*;

import java.io.*;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class HttpApiServletTest {

    private SpecDriven sdk;
    private HttpApiServlet servlet;

    @BeforeEach
    void setUp() {
        sdk = SpecDriven.builder()
                .registerTool(new StubTool("bash", "run commands",
                        List.of(new ToolParameter("command", "string", "the command", true))))
                .build();
        servlet = new HttpApiServlet(sdk);
        servlet.init();
    }

    // --- Route dispatching ---

    @Nested
    class RouteDispatching {

        @Test
        void getHealth_returns200() {
            StubResponse resp = service("GET", "/health");
            assertEquals(200, resp.status());
            assertTrue(resp.body().contains("\"status\":\"ok\""));
            assertTrue(resp.body().contains("\"version\":\"0.1.0\""));
        }

        @Test
        void getTools_returns200() {
            StubResponse resp = service("GET", "/tools");
            assertEquals(200, resp.status());
            assertTrue(resp.body().contains("\"tools\":["));
            assertTrue(resp.body().contains("\"name\":\"bash\""));
        }

        @Test
        void postAgentRun_returns200() {
            StubResponse resp = service("POST", "/agent/run", "{\"prompt\":\"hello\"}");
            assertEquals(200, resp.status());
            assertTrue(resp.body().contains("\"agentId\":"));
            assertTrue(resp.body().contains("\"state\":\"STOPPED\""));
        }

        @Test
        void getAgentState_withValidId_returns200() {
            String agentId = createAgent();
            StubRequest req = new StubRequest("GET", "/agent/state");
            req.queryParams.put("id", agentId);
            StubResponse resp = new StubResponse();
            servlet.service(req, resp);
            assertEquals(200, resp.status());
            assertTrue(resp.body().contains("\"agentId\":\"" + agentId + "\""));
        }

        @Test
        void postAgentStop_withValidId_returns200() {
            String agentId = createAgent();
            StubRequest req = new StubRequest("POST", "/agent/stop");
            req.queryParams.put("id", agentId);
            StubResponse resp = new StubResponse();
            servlet.service(req, resp);
            assertEquals(200, resp.status());
        }
    }

    // --- POST /agent/run ---

    @Nested
    class AgentRunTests {

        @Test
        void validRequest_returnsAgentIdAndOutput() {
            StubResponse resp = service("POST", "/agent/run", "{\"prompt\":\"explain this\"}");
            assertEquals(200, resp.status());
            assertTrue(resp.body().contains("\"agentId\":"));
            assertTrue(resp.body().contains("\"output\":"));
            assertTrue(resp.body().contains("\"state\":\"STOPPED\""));
        }

        @Test
        void missingPrompt_returns400() {
            StubResponse resp = service("POST", "/agent/run", "{\"systemPrompt\":\"be helpful\"}");
            assertEquals(400, resp.status());
            assertTrue(resp.body().contains("\"error\":\"invalid_params\""));
        }

        @Test
        void emptyBody_returns400() {
            StubResponse resp = service("POST", "/agent/run", "");
            assertEquals(400, resp.status());
            assertTrue(resp.body().contains("\"error\":\"invalid_params\""));
        }

        @Test
        void wrongMethod_returns405() {
            StubResponse resp = service("GET", "/agent/run");
            assertEquals(405, resp.status());
            assertTrue(resp.body().contains("\"error\":\"method_not_allowed\""));
        }
    }

    // --- POST /agent/stop ---

    @Nested
    class AgentStopTests {

        @Test
        void existingAgent_returns200() {
            String agentId = createAgent();
            StubRequest req = new StubRequest("POST", "/agent/stop");
            req.queryParams.put("id", agentId);
            StubResponse resp = new StubResponse();
            servlet.service(req, resp);
            assertEquals(200, resp.status());
        }

        @Test
        void unknownAgentId_returns404() {
            StubRequest req = new StubRequest("POST", "/agent/stop");
            req.queryParams.put("id", "nonexistent-id");
            StubResponse resp = new StubResponse();
            servlet.service(req, resp);
            assertEquals(404, resp.status());
            assertTrue(resp.body().contains("\"error\":\"not_found\""));
        }

        @Test
        void missingIdParam_returns400() {
            StubResponse resp = service("POST", "/agent/stop");
            assertEquals(400, resp.status());
            assertTrue(resp.body().contains("\"error\":\"invalid_params\""));
        }
    }

    // --- GET /agent/state ---

    @Nested
    class AgentStateTests {

        @Test
        void existingAgent_returnsState() {
            String agentId = createAgent();
            StubRequest req = new StubRequest("GET", "/agent/state");
            req.queryParams.put("id", agentId);
            StubResponse resp = new StubResponse();
            servlet.service(req, resp);
            assertEquals(200, resp.status());
            assertTrue(resp.body().contains("\"agentId\":\"" + agentId + "\""));
            assertTrue(resp.body().contains("\"createdAt\":"));
            assertTrue(resp.body().contains("\"updatedAt\":"));
        }

        @Test
        void unknownAgentId_returns404() {
            StubRequest req = new StubRequest("GET", "/agent/state");
            req.queryParams.put("id", "no-such-id");
            StubResponse resp = new StubResponse();
            servlet.service(req, resp);
            assertEquals(404, resp.status());
        }

        @Test
        void missingIdParam_returns400() {
            StubResponse resp = service("GET", "/agent/state");
            assertEquals(400, resp.status());
        }
    }

    // --- GET /tools ---

    @Nested
    class ToolsTests {

        @Test
        void returnsRegisteredTools() {
            StubResponse resp = service("GET", "/tools");
            assertEquals(200, resp.status());
            assertTrue(resp.body().contains("\"name\":\"bash\""));
            assertTrue(resp.body().contains("\"command\""));
        }

        @Test
        void wrongMethod_returns405() {
            StubResponse resp = service("POST", "/tools", "{}");
            assertEquals(405, resp.status());
        }

        @Test
        void registerRemoteTool_returnsMetadataAndListsTool() {
            StubResponse register = service("POST", "/tools/register", """
                    {"name":"lookup","description":"lookup data","callbackUrl":"http://localhost/callback","parameters":[{"name":"term","type":"string","description":"search term","required":true}]}
                    """);
            assertEquals(200, register.status());
            assertTrue(register.body().contains("\"name\":\"lookup\""));
            assertTrue(register.body().contains("\"term\""));

            StubResponse list = service("GET", "/tools");
            assertEquals(200, list.status());
            assertTrue(list.body().contains("\"name\":\"bash\""));
            assertTrue(list.body().contains("\"name\":\"lookup\""));
        }

        @Test
        void registerRemoteTool_missingNameReturns400() {
            StubResponse resp = service("POST", "/tools/register", """
                    {"description":"lookup data","callbackUrl":"http://localhost/callback","parameters":[]}
                    """);
            assertEquals(400, resp.status());
            assertTrue(resp.body().contains("\"error\":\"invalid_params\""));
        }

        @Test
        void registerRemoteTool_missingCallbackUrlReturns400() {
            StubResponse resp = service("POST", "/tools/register", """
                    {"name":"lookup","description":"lookup data","parameters":[]}
                    """);
            assertEquals(400, resp.status());
            assertTrue(resp.body().contains("\"error\":\"invalid_params\""));
        }

        @Test
        void registerRemoteTool_cannotOverwriteStaticTool() {
            StubResponse resp = service("POST", "/tools/register", """
                    {"name":"bash","description":"replacement","callbackUrl":"http://localhost/callback","parameters":[]}
                    """);
            assertEquals(409, resp.status());
            assertTrue(resp.body().contains("\"error\":\"conflict\""));
        }

        @Test
        void registerRemoteTool_replacesPreviousRemoteRegistration() {
            StubResponse first = service("POST", "/tools/register", """
                    {"name":"lookup","description":"first","callbackUrl":"http://localhost/first","parameters":[]}
                    """);
            assertEquals(200, first.status());
            StubResponse second = service("POST", "/tools/register", """
                    {"name":"lookup","description":"second","callbackUrl":"http://localhost/second","parameters":[]}
                    """);
            assertEquals(200, second.status());

            StubResponse list = service("GET", "/tools");
            assertEquals(200, list.status());
            assertTrue(list.body().contains("\"description\":\"second\""));
            assertFalse(list.body().contains("\"description\":\"first\""));
        }
    }

    // --- GET /health ---

    @Nested
    class HealthTests {

        @Test
        void returnsOkStatus() {
            StubResponse resp = service("GET", "/health");
            assertEquals(200, resp.status());
            assertTrue(resp.body().contains("\"status\":\"ok\""));
            assertTrue(resp.body().contains("\"version\":\"0.1.0\""));
        }

        @Test
        void wrongMethod_returns405() {
            StubResponse resp = service("POST", "/health", "{}");
            assertEquals(405, resp.status());
        }
    }

    // --- GET /events ---

    @Nested
    class EventsTests {

        @Test
        void pollEvents_returnsObservedEventsInSequenceOrder() {
            publish(EventType.AGENT_STATE_CHANGED, "agent-1", Map.of("state", "RUNNING"));
            publish(EventType.ERROR, "agent-1", Map.of("message", "failed"));

            StubResponse resp = service("GET", "/events");

            assertEquals(200, resp.status());
            assertTrue(resp.body().contains("\"sequence\":1"));
            assertTrue(resp.body().contains("\"sequence\":2"));
            assertTrue(resp.body().contains("\"type\":\"AGENT_STATE_CHANGED\""));
            assertTrue(resp.body().contains("\"type\":\"ERROR\""));
            assertTrue(resp.body().contains("\"nextCursor\":2"));
            assertTrue(resp.body().indexOf("\"sequence\":1") < resp.body().indexOf("\"sequence\":2"));
        }

        @Test
        void pollEvents_usesAfterCursor() {
            publish(EventType.AGENT_STATE_CHANGED, "agent-1", Map.of("state", "RUNNING"));
            publish(EventType.AGENT_STATE_CHANGED, "agent-1", Map.of("state", "STOPPED"));
            publish(EventType.ERROR, "agent-1", Map.of("message", "failed"));
            StubRequest req = new StubRequest("GET", "/events");
            req.queryParams.put("after", "1");
            StubResponse resp = new StubResponse();

            servlet.service(req, resp);

            assertEquals(200, resp.status());
            assertFalse(resp.body().contains("\"sequence\":1"));
            assertTrue(resp.body().contains("\"sequence\":2"));
            assertTrue(resp.body().contains("\"sequence\":3"));
            assertTrue(resp.body().contains("\"nextCursor\":3"));
        }

        @Test
        void pollEvents_appliesLimit() {
            publish(EventType.AGENT_STATE_CHANGED, "agent-1", Map.of("state", "RUNNING"));
            publish(EventType.AGENT_STATE_CHANGED, "agent-1", Map.of("state", "STOPPED"));
            publish(EventType.ERROR, "agent-1", Map.of("message", "failed"));
            StubRequest req = new StubRequest("GET", "/events");
            req.queryParams.put("limit", "2");
            StubResponse resp = new StubResponse();

            servlet.service(req, resp);

            assertEquals(200, resp.status());
            assertTrue(resp.body().contains("\"sequence\":1"));
            assertTrue(resp.body().contains("\"sequence\":2"));
            assertFalse(resp.body().contains("\"sequence\":3"));
            assertTrue(resp.body().contains("\"nextCursor\":2"));
        }

        @Test
        void pollEvents_filtersByType() {
            publish(EventType.AGENT_STATE_CHANGED, "agent-1", Map.of("state", "RUNNING"));
            publish(EventType.ERROR, "agent-1", Map.of("message", "failed"));
            StubRequest req = new StubRequest("GET", "/events");
            req.queryParams.put("type", "ERROR");
            StubResponse resp = new StubResponse();

            servlet.service(req, resp);

            assertEquals(200, resp.status());
            assertFalse(resp.body().contains("\"type\":\"AGENT_STATE_CHANGED\""));
            assertTrue(resp.body().contains("\"type\":\"ERROR\""));
            assertTrue(resp.body().contains("\"nextCursor\":2"));
        }

        @Test
        void pollEvents_emptyReturnsEmptyListAndCursor() {
            StubResponse resp = service("GET", "/events");

            assertEquals(200, resp.status());
            assertTrue(resp.body().contains("\"events\":[]"));
            assertTrue(resp.body().contains("\"nextCursor\":0"));
        }

        @Test
        void pollEvents_invalidQueryValuesReturn400() {
            StubRequest afterReq = new StubRequest("GET", "/events");
            afterReq.queryParams.put("after", "-1");
            StubResponse afterResp = new StubResponse();
            servlet.service(afterReq, afterResp);
            assertEquals(400, afterResp.status());

            StubRequest limitReq = new StubRequest("GET", "/events");
            limitReq.queryParams.put("limit", "0");
            StubResponse limitResp = new StubResponse();
            servlet.service(limitReq, limitResp);
            assertEquals(400, limitResp.status());

            StubRequest typeReq = new StubRequest("GET", "/events");
            typeReq.queryParams.put("type", "NO_SUCH_EVENT");
            StubResponse typeResp = new StubResponse();
            servlet.service(typeReq, typeResp);
            assertEquals(400, typeResp.status());
            assertTrue(typeResp.body().contains("\"error\":\"invalid_params\""));
        }

        @Test
        void pollEvents_wrongMethodReturns405() {
            StubResponse resp = service("POST", "/events", "{}");

            assertEquals(405, resp.status());
            assertTrue(resp.body().contains("\"error\":\"method_not_allowed\""));
        }
    }

    // --- Error handling ---

    @Nested
    class ErrorHandling {

        @Test
        void invalidRoute_returns404() {
            StubResponse resp = service("GET", "/unknown");
            assertEquals(404, resp.status());
            assertTrue(resp.body().contains("\"error\":\"not_found\""));
        }

        @Test
        void rootPath_returns404() {
            StubResponse resp = service("GET", "/");
            assertEquals(404, resp.status());
        }

        @Test
        void unknownAgentAction_returns404() {
            StubResponse resp = service("GET", "/agent/unknown");
            assertEquals(404, resp.status());
            assertTrue(resp.body().contains("Unknown agent action"));
        }

        @Test
        void nullPathInfo_returns404() {
            StubRequest req = new StubRequest("GET", null);
            StubResponse resp = new StubResponse();
            servlet.service(req, resp);
            assertEquals(404, resp.status());
        }

        @Test
        void errorResponsesAreJson() {
            StubResponse resp = service("GET", "/nonexistent");
            assertTrue(resp.contentType().contains("application/json"));
        }
    }

    // --- Exception mapping ---

    @Nested
    class ExceptionMapping {

        @Test
        void sdkLlmException_mapsTo502() {
            HttpApiException mapped = servlet.mapException(new SdkLlmException("LLM failed", new RuntimeException()));
            assertEquals(502, mapped.httpStatus());
            assertEquals("llm_error", mapped.errorCode());
        }

        @Test
        void sdkPermissionException_mapsTo403() {
            HttpApiException mapped = servlet.mapException(new SdkPermissionException("denied", new RuntimeException()));
            assertEquals(403, mapped.httpStatus());
            assertEquals("permission_denied", mapped.errorCode());
        }

        @Test
        void sdkToolException_mapsTo422() {
            HttpApiException mapped = servlet.mapException(new SdkToolException("tool error", new RuntimeException()));
            assertEquals(422, mapped.httpStatus());
            assertEquals("tool_error", mapped.errorCode());
        }

        @Test
        void sdkVaultException_mapsTo500() {
            HttpApiException mapped = servlet.mapException(new SdkVaultException("vault error", new RuntimeException()));
            assertEquals(500, mapped.httpStatus());
            assertEquals("vault_error", mapped.errorCode());
        }

        @Test
        void sdkConfigException_mapsTo500() {
            HttpApiException mapped = servlet.mapException(new SdkConfigException("config error", new RuntimeException()));
            assertEquals(500, mapped.httpStatus());
            assertEquals("config_error", mapped.errorCode());
        }

        @Test
        void genericException_mapsTo500() {
            HttpApiException mapped = servlet.mapException(new RuntimeException("boom"));
            assertEquals(500, mapped.httpStatus());
            assertEquals("internal", mapped.errorCode());
        }

        @Test
        void httpApiException_passesThrough() {
            HttpApiException original = new HttpApiException(418, "teapot", "I'm a teapot");
            HttpApiException mapped = servlet.mapException(original);
            assertSame(original, mapped);
        }
    }

    // --- Agent tracking across operations ---

    @Nested
    class AgentTracking {

        @Test
        void runThenState_tracksAgent() {
            String agentId = createAgent();
            StubRequest stateReq = new StubRequest("GET", "/agent/state");
            stateReq.queryParams.put("id", agentId);
            StubResponse stateResp = new StubResponse();
            servlet.service(stateReq, stateResp);
            assertEquals(200, stateResp.status());
        }

        @Test
        void runThenStopThenState_tracksAgent() {
            String agentId = createAgent();

            // Stop
            StubRequest stopReq = new StubRequest("POST", "/agent/stop");
            stopReq.queryParams.put("id", agentId);
            StubResponse stopResp = new StubResponse();
            servlet.service(stopReq, stopResp);
            assertEquals(200, stopResp.status());

            // State still accessible after stop
            StubRequest stateReq = new StubRequest("GET", "/agent/state");
            stateReq.queryParams.put("id", agentId);
            StubResponse stateResp = new StubResponse();
            servlet.service(stateReq, stateResp);
            assertEquals(200, stateResp.status());
            assertTrue(stateResp.body().contains("\"state\":\"STOPPED\""));
        }
    }

    // --- Helpers ---

    private StubResponse service(String method, String path) {
        return service(method, path, null);
    }

    private StubResponse service(String method, String path, String body) {
        StubRequest req = new StubRequest(method, path, body);
        StubResponse resp = new StubResponse();
        servlet.service(req, resp);
        return resp;
    }

    private String createAgent() {
        StubResponse resp = service("POST", "/agent/run", "{\"prompt\":\"test\"}");
        assertEquals(200, resp.status());
        return extractJsonValue(resp.body(), "agentId");
    }

    private void publish(EventType type, String source, Map<String, Object> metadata) {
        sdk.eventBus().publish(new Event(type, System.currentTimeMillis(), source, metadata));
    }

    private String extractJsonValue(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) return null;
        start += needle.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    // --- Stub Tool ---

    record StubTool(String name, String description, List<ToolParameter> parameters) implements Tool {
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

    // --- Stub Servlet Types ---

    static class StubRequest extends HttpServletRequestWrapper {
        private final String method;
        private final String pathInfo;
        private final String body;
        final Map<String, String> queryParams = new HashMap<>();

        StubRequest(String method, String pathInfo) {
            this(method, pathInfo, null);
        }

        StubRequest(String method, String pathInfo, String body) {
            super(new NullRequest());
            this.method = method;
            this.pathInfo = pathInfo;
            this.body = body;
        }

        @Override public String getMethod() { return method; }
        @Override public String getPathInfo() { return pathInfo; }
        @Override public String getParameter(String name) { return queryParams.get(name); }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new StringReader(body != null ? body : ""));
        }
    }

    static class StubResponse extends HttpServletResponseWrapper {
        private int status = 200;
        private String contentType;
        private final StringWriter writer = new StringWriter();

        StubResponse() { super(new NullResponse()); }

        @Override public void setStatus(int sc) { this.status = sc; }
        @Override public void setContentType(String type) { this.contentType = type; }
        @Override public PrintWriter getWriter() { return new PrintWriter(writer, true); }

        int status() { return status; }
        String body() { return writer.toString(); }
        String contentType() { return contentType != null ? contentType : ""; }
    }

    /** Null-object HttpServletRequest that returns safe defaults for every method. */
    private static class NullRequest implements HttpServletRequest {
        public Object getAttribute(String n) { return null; }
        public Enumeration<String> getAttributeNames() { return Collections.emptyEnumeration(); }
        public void setAttribute(String n, Object o) {}
        public void removeAttribute(String n) {}
        public String getCharacterEncoding() { return "UTF-8"; }
        public void setCharacterEncoding(String e) {}
        public int getContentLength() { return -1; }
        public long getContentLengthLong() { return -1; }
        public String getContentType() { return null; }
        public ServletInputStream getInputStream() { return null; }
        public String getParameter(String n) { return null; }
        public Enumeration<String> getParameterNames() { return Collections.emptyEnumeration(); }
        public String[] getParameterValues(String n) { return null; }
        public Map<String, String[]> getParameterMap() { return Map.of(); }
        public String getProtocol() { return "HTTP/1.1"; }
        public String getScheme() { return "http"; }
        public String getServerName() { return "localhost"; }
        public int getServerPort() { return 8080; }
        public BufferedReader getReader() { return new BufferedReader(new StringReader("")); }
        public String getRemoteAddr() { return "127.0.0.1"; }
        public String getRemoteHost() { return "localhost"; }
        public Locale getLocale() { return Locale.ENGLISH; }
        public Enumeration<Locale> getLocales() { return Collections.enumeration(List.of(Locale.ENGLISH)); }
        public boolean isSecure() { return false; }
        public RequestDispatcher getRequestDispatcher(String p) { return null; }
        public int getRemotePort() { return 0; }
        public String getLocalName() { return "localhost"; }
        public String getLocalAddr() { return "127.0.0.1"; }
        public int getLocalPort() { return 8080; }
        public ServletContext getServletContext() { return null; }
        public AsyncContext startAsync() throws IllegalStateException { throw new IllegalStateException(); }
        public AsyncContext startAsync(ServletRequest req, ServletResponse resp) throws IllegalStateException { throw new IllegalStateException(); }
        public boolean isAsyncStarted() { return false; }
        public boolean isAsyncSupported() { return false; }
        public AsyncContext getAsyncContext() { return null; }
        public DispatcherType getDispatcherType() { return DispatcherType.REQUEST; }
        public String getRequestId() { return ""; }
        public String getProtocolRequestId() { return ""; }
        public ServletConnection getServletConnection() { return null; }
        public String getAuthType() { return null; }
        public jakarta.servlet.http.Cookie[] getCookies() { return null; }
        public long getDateHeader(String n) { return -1; }
        public String getHeader(String n) { return null; }
        public Enumeration<String> getHeaders(String n) { return Collections.emptyEnumeration(); }
        public Enumeration<String> getHeaderNames() { return Collections.emptyEnumeration(); }
        public int getIntHeader(String n) { return -1; }
        public String getMethod() { return "GET"; }
        public String getPathInfo() { return null; }
        public String getPathTranslated() { return null; }
        public String getContextPath() { return "/api/v1"; }
        public String getQueryString() { return null; }
        public String getRemoteUser() { return null; }
        public boolean isUserInRole(String r) { return false; }
        public Principal getUserPrincipal() { return null; }
        public String getRequestedSessionId() { return null; }
        public StringBuffer getRequestURL() { return new StringBuffer("http://localhost:8080/api/v1"); }
        public String getRequestURI() { return "/api/v1"; }
        public String getServletPath() { return ""; }
        public HttpSession getSession(boolean c) { return null; }
        public HttpSession getSession() { return null; }
        public String changeSessionId() { return null; }
        public boolean isRequestedSessionIdValid() { return false; }
        public boolean isRequestedSessionIdFromCookie() { return false; }
        public boolean isRequestedSessionIdFromURL() { return false; }
        public boolean authenticate(HttpServletResponse r) { return false; }
        public void login(String u, String p) {}
        public void logout() {}
        public Collection<Part> getParts() { return List.of(); }
        public Part getPart(String n) { return null; }
        public <T extends HttpUpgradeHandler> T upgrade(Class<T> c) { return null; }
    }

    /** Null-object HttpServletResponse that returns safe defaults for every method. */
    private static class NullResponse implements HttpServletResponse {
        public String getCharacterEncoding() { return "UTF-8"; }
        public String getContentType() { return null; }
        public void setCharacterEncoding(String c) {}
        public void setContentLength(int len) {}
        public void setContentLengthLong(long len) {}
        public void setContentType(String t) {}
        public void setBufferSize(int s) {}
        public int getBufferSize() { return 0; }
        public void flushBuffer() {}
        public void resetBuffer() {}
        public boolean isCommitted() { return false; }
        public void reset() {}
        public void setLocale(Locale l) {}
        public Locale getLocale() { return Locale.ENGLISH; }
        public void addCookie(jakarta.servlet.http.Cookie c) {}
        public boolean containsHeader(String n) { return false; }
        public String encodeURL(String u) { return u; }
        public String encodeRedirectURL(String u) { return u; }
        public String encodeUrl(String u) { return u; }
        public String encodeRedirectUrl(String u) { return u; }
        public void sendError(int sc, String msg) {}
        public void sendError(int sc) {}
        public void sendRedirect(String l) {}
        public void sendRedirect(String l, int sc, boolean clear) {}
        public void setDateHeader(String n, long d) {}
        public void addDateHeader(String n, long d) {}
        public void setHeader(String n, String v) {}
        public void addHeader(String n, String v) {}
        public void setIntHeader(String n, int v) {}
        public void addIntHeader(String n, int v) {}
        public void setStatus(int sc) {}
        public void setStatus(int sc, String sm) {}
        public int getStatus() { return 200; }
        public String getHeader(String n) { return null; }
        public Collection<String> getHeaders(String n) { return List.of(); }
        public Collection<String> getHeaderNames() { return List.of(); }
        public PrintWriter getWriter() { return new PrintWriter(new StringWriter()); }
        public ServletOutputStream getOutputStream() { return null; }
    }
}
