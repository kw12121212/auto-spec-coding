package org.specdriven.agent.http;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.specdriven.agent.agent.AgentState;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.question.DeliveryAttempt;
import org.specdriven.agent.question.DeliveryLogStore;
import org.specdriven.agent.question.ReplyCallbackRouter;
import org.specdriven.agent.tool.Tool;
import org.specdriven.agent.tool.ToolParameter;
import org.specdriven.sdk.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP servlet that maps {@code /api/v1/*} requests to SDK operations and
 * {@code /services/*} requests to application service invocations.
 */
public class HttpApiServlet extends HttpServlet {

    private static final String VERSION = "0.1.0";
    private static final int EVENT_BUFFER_CAPACITY = 1024;
    private static final int EVENT_POLL_DEFAULT_LIMIT = 100;
    private static final int EVENT_POLL_MAX_LIMIT = 500;

    private SpecDriven sdk;
    private ReplyCallbackRouter callbackRouter;
    private DeliveryLogStore deliveryLogStore;
    private ServiceInvoker serviceInvoker;
    private final Map<String, TrackedAgent> agents = new ConcurrentHashMap<>();
    private final HttpEventBuffer eventBuffer = new HttpEventBuffer(EVENT_BUFFER_CAPACITY);
    private volatile boolean eventBufferSubscribed;

    /** Default constructor — SDK is created in {@link #init()}. */
    public HttpApiServlet() {}

    /** Dependency-injection constructor — used when SDK is assembled externally. */
    public HttpApiServlet(SpecDriven sdk) {
        this.sdk = sdk;
    }

    /** Full dependency-injection constructor with callback router. */
    public HttpApiServlet(SpecDriven sdk, ReplyCallbackRouter callbackRouter) {
        this.sdk = sdk;
        this.callbackRouter = callbackRouter;
    }

    /** Full dependency-injection constructor with callback router and delivery log store. */
    public HttpApiServlet(SpecDriven sdk, ReplyCallbackRouter callbackRouter, DeliveryLogStore deliveryLogStore) {
        this.sdk = sdk;
        this.callbackRouter = callbackRouter;
        this.deliveryLogStore = deliveryLogStore;
    }

    HttpApiServlet(SpecDriven sdk, ServiceInvoker serviceInvoker) {
        this.sdk = sdk;
        this.serviceInvoker = serviceInvoker;
    }

    @Override
    public void init() {
        if (sdk == null) {
            sdk = SpecDriven.builder().build();
        }
        if (serviceInvoker == null && sdk.platform() != null) {
            serviceInvoker = new LealoneServiceInvoker(sdk.platform().database().jdbcUrl());
        }
        ensureEventBufferSubscribed();
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) {
        try {
            ensureEventBufferSubscribed();
            Route route = parseRoute(req);
            String json = dispatch(route, req);
            sendJson(resp, responseStatus(route), json);
        } catch (HttpApiException e) {
            sendJson(resp, e.httpStatus(), HttpJsonCodec.encode(e.toErrorResponse()));
        } catch (Exception e) {
            HttpApiException mapped = mapException(e);
            sendJson(resp, mapped.httpStatus(), HttpJsonCodec.encode(mapped.toErrorResponse()));
        }
    }

    private int responseStatus(Route route) {
        if ("POST".equals(route.method()) && "workflows".equals(route.segment(1)) && route.length() == 2) {
            return 202;
        }
        return 200;
    }

    // --- Route parsing ---

    private Route parseRoute(HttpServletRequest req) {
        String pathInfo = req.getPathInfo();
        if ("/services".equals(req.getServletPath()) && pathInfo != null && !pathInfo.startsWith("/services/")) {
            pathInfo = "/services" + pathInfo;
        }
        if (pathInfo == null || "/".equals(pathInfo)) {
            throw new HttpApiException(404, "not_found", "No route specified");
        }
        return new Route(req.getMethod(), pathInfo.split("/"));
    }

    // --- Dispatch ---

    private String dispatch(Route route, HttpServletRequest req) {
        String group = route.segment(1);
        if ("agent".equals(group) && route.length() >= 3) {
            return dispatchAgent(route.method(), route.segment(2), req);
        }
        if ("workflows".equals(group) && route.length() >= 2) {
            return dispatchWorkflow(route, req);
        }
        if ("tools".equals(group) && route.length() >= 2) {
            return dispatchTools(route.method(), route.segment(2), req);
        }
        if ("health".equals(group) && route.length() >= 2) {
            requireGet(route.method(), "/health");
            return handleHealth();
        }
        if ("platform".equals(group) && route.length() >= 3 && "health".equals(route.segment(2))) {
            requireGet(route.method(), "/platform/health");
            return handlePlatformHealth();
        }
        if ("events".equals(group) && route.length() >= 2) {
            requireGet(route.method(), "/events");
            return handleEvents(req);
        }
        if ("callbacks".equals(group) && route.length() >= 3) {
            requirePost(route.method(), "/callbacks/" + route.segment(2));
            return handleCallback(route.segment(2), req);
        }
        if ("delivery".equals(group) && route.length() >= 4
                && "status".equals(route.segment(2))) {
            requireGet(route.method(), "/delivery/status/" + route.segment(3));
            return handleDeliveryStatus(route.segment(3));
        }
        if ("services".equals(group) && route.length() >= 2) {
            return dispatchService(route.method(), route, req);
        }
        throw new HttpApiException(404, "not_found", "Unknown route");
    }

    private String dispatchWorkflow(Route route, HttpServletRequest req) {
        if (route.length() == 2) {
            requirePost(route.method(), "/workflows");
            return handleWorkflowStart(req);
        }
        String workflowId = route.segment(2);
        if (workflowId == null || workflowId.isBlank()) {
            throw new HttpApiException(404, "not_found", "Unknown workflow route");
        }
        if (route.length() == 3) {
            requireGet(route.method(), "/workflows/" + workflowId);
            return handleWorkflowState(workflowId);
        }
        if (route.length() == 4 && "result".equals(route.segment(3))) {
            requireGet(route.method(), "/workflows/" + workflowId + "/result");
            return handleWorkflowResult(workflowId);
        }
        throw new HttpApiException(404, "not_found", "Unknown workflow route");
    }

    private String dispatchAgent(String method, String action, HttpServletRequest req) {
        return switch (action) {
            case "run" -> {
                requirePost(method, "/agent/run");
                yield handleAgentRun(req);
            }
            case "stop" -> {
                requirePost(method, "/agent/stop");
                yield handleAgentStop(req);
            }
            case "state" -> {
                requireGet(method, "/agent/state");
                yield handleAgentState(req);
            }
            default -> throw new HttpApiException(404, "not_found", "Unknown agent action: " + action);
        };
    }

    private String dispatchTools(String method, String action, HttpServletRequest req) {
        if (action == null) {
            requireGet(method, "/tools");
            return handleToolsList();
        }
        if ("register".equals(action)) {
            requirePost(method, "/tools/register");
            return handleToolRegister(req);
        }
        throw new HttpApiException(404, "not_found", "Unknown tools action: " + action);
    }

    private String dispatchService(String method, Route route, HttpServletRequest req) {
        if (route.length() != 4 || route.segment(2) == null || route.segment(2).isBlank()
                || route.segment(3) == null || route.segment(3).isBlank()) {
            throw new HttpApiException(404, "not_found", "Unknown service route");
        }
        requirePost(method, "/services/" + route.segment(2) + "/" + route.segment(3));
        return handleServiceInvocation(route.segment(2), route.segment(3), req);
    }

    // --- Handlers ---

    private String handleAgentRun(HttpServletRequest req) {
        String body = readBody(req);
        if (body == null || body.isBlank()) {
            throw new HttpApiException(400, "invalid_params", "Request body required");
        }

        RunAgentRequest request = HttpJsonCodec.decodeRequest(body);
        SdkAgent agent = sdk.createAgent();
        String agentId = UUID.randomUUID().toString();
        long createdAt = System.currentTimeMillis();
        agents.put(agentId, new TrackedAgent(agent, createdAt));

        try {
            String output = agent.run(request.prompt());
            long updatedAt = System.currentTimeMillis();
            agents.put(agentId, new TrackedAgent(agent, createdAt, updatedAt));
            return HttpJsonCodec.encode(new RunAgentResponse(agentId, output != null ? output : "", "STOPPED"));
        } catch (Exception e) {
            long updatedAt = System.currentTimeMillis();
            agents.put(agentId, new TrackedAgent(agent, createdAt, updatedAt));
            throw e;
        }
    }

    private String handleAgentStop(HttpServletRequest req) {
        String id = req.getParameter("id");
        if (id == null || id.isBlank()) {
            throw new HttpApiException(400, "invalid_params", "Query param 'id' required");
        }
        TrackedAgent tracked = agents.get(id);
        if (tracked == null) {
            throw new HttpApiException(404, "not_found", "Agent not found: " + id);
        }
        tracked.agent.stop();
        return null; // 200 empty
    }

    private String handleAgentState(HttpServletRequest req) {
        String id = req.getParameter("id");
        if (id == null || id.isBlank()) {
            throw new HttpApiException(400, "invalid_params", "Query param 'id' required");
        }
        TrackedAgent tracked = agents.get(id);
        if (tracked == null) {
            throw new HttpApiException(404, "not_found", "Agent not found: " + id);
        }
        AgentState state = tracked.agent.getState();
        return HttpJsonCodec.encode(new AgentStateResponse(id, state.name(), tracked.createdAt, tracked.updatedAt));
    }

    private String handleToolsList() {
        List<ToolInfo> toolInfos = new ArrayList<>();
        for (Tool tool : sdk.tools()) {
            toolInfos.add(toolInfo(tool));
        }
        return HttpJsonCodec.encode(new ToolsListResponse(toolInfos));
    }

    private String handleWorkflowStart(HttpServletRequest req) {
        String body = readBody(req);
        if (body == null || body.isBlank()) {
            throw new HttpApiException(400, "invalid_params", "Request body required");
        }
        WorkflowStartRequest request = HttpJsonCodec.decodeWorkflowStartRequest(body);
        try {
            WorkflowInstanceView view = sdk.startWorkflow(request.workflowName(), request.input());
            return HttpJsonCodec.encode(new WorkflowInstanceResponse(
                    view.workflowId(),
                    view.workflowName(),
                    view.status().name(),
                    view.createdAt(),
                    view.updatedAt()));
        } catch (IllegalArgumentException e) {
            throw new HttpApiException(404, "not_found", e.getMessage());
        }
    }

    private String handleWorkflowState(String workflowId) {
        try {
            WorkflowInstanceView view = sdk.workflowState(workflowId);
            return HttpJsonCodec.encode(new WorkflowInstanceResponse(
                    view.workflowId(),
                    view.workflowName(),
                    view.status().name(),
                    view.createdAt(),
                    view.updatedAt()));
        } catch (IllegalArgumentException e) {
            throw new HttpApiException(404, "not_found", e.getMessage());
        }
    }

    private String handleWorkflowResult(String workflowId) {
        try {
            WorkflowResultView view = sdk.workflowResult(workflowId);
            return HttpJsonCodec.encode(new WorkflowResultResponse(
                    view.workflowId(),
                    view.workflowName(),
                    view.status().name(),
                    view.result(),
                    view.failureSummary(),
                    view.createdAt(),
                    view.updatedAt()));
        } catch (IllegalArgumentException e) {
            throw new HttpApiException(404, "not_found", e.getMessage());
        }
    }

    private String handleServiceInvocation(String serviceName, String methodName, HttpServletRequest req) {
        String body = readBody(req);
        return ServiceHttpInvocationHandler.invoke(serviceInvoker, serviceName, methodName, body);
    }

    private String handleToolRegister(HttpServletRequest req) {
        String body = readBody(req);
        if (body == null || body.isBlank()) {
            throw new HttpApiException(400, "invalid_params", "Request body required");
        }
        RemoteToolRegistrationRequest request = HttpJsonCodec.decodeRemoteToolRegistrationRequest(body);
        String name = requireRequestText(request.name(), "name");
        requireRequestText(request.callbackUrl(), "callbackUrl");
        if (sdk.hasStaticTool(name)) {
            throw new HttpApiException(409, "conflict", "Tool already exists: " + name);
        }
        RemoteHttpTool tool;
        try {
            tool = new RemoteHttpTool(request);
        } catch (IllegalArgumentException e) {
            throw new HttpApiException(400, "invalid_params", e.getMessage());
        }
        sdk.registerRemoteTool(tool);
        return HttpJsonCodec.encode(toolInfo(tool));
    }

    private ToolInfo toolInfo(Tool tool) {
        List<Map<String, Object>> params = new ArrayList<>();
        for (ToolParameter p : tool.getParameters()) {
            Map<String, Object> paramInfo = new LinkedHashMap<>();
            paramInfo.put("name", p.name());
            paramInfo.put("type", p.type());
            paramInfo.put("description", p.description());
            paramInfo.put("required", p.required());
            params.add(paramInfo);
        }
        return new ToolInfo(tool.getName(), tool.getDescription(), params);
    }

    private String requireRequestText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new HttpApiException(400, "invalid_params", "Missing required field: " + field);
        }
        return value.trim();
    }

    private String handleHealth() {
        return HttpJsonCodec.encode(new HealthResponse("ok", VERSION));
    }

    private String handlePlatformHealth() {
        if (sdk == null || sdk.platform() == null) {
            throw new HttpApiException(404, "not_found", "Platform not assembled");
        }
        return HttpJsonCodec.encode(PlatformHealthResponse.from(sdk.platform().checkHealth()));
    }

    private String handleEvents(HttpServletRequest req) {
        long after = parseNonNegativeLong(req.getParameter("after"), "after", 0L);
        int limit = parseLimit(req.getParameter("limit"));
        EventType type = parseEventType(req.getParameter("type"));
        return encodeEventPage(eventBuffer.poll(after, limit, type));
    }

    private String handleCallback(String channelType, HttpServletRequest req) {
        if (callbackRouter == null) {
            throw new HttpApiException(404, "not_found", "No callback router configured");
        }
        String body = readBody(req);
        if (body == null || body.isBlank()) {
            throw new HttpApiException(400, "invalid_params", "Request body required");
        }
        Map<String, String> headers = extractCallbackHeaders(req);
        try {
            callbackRouter.dispatch(channelType, body, headers);
        } catch (IllegalArgumentException e) {
            throw new HttpApiException(404, "not_found", e.getMessage());
        } catch (org.specdriven.agent.question.MobileAdapterException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("invalid")) {
                throw new HttpApiException(401, "unauthorized", e.getMessage());
            }
            throw new HttpApiException(400, "callback_error", e.getMessage());
        }
        return null; // 200 empty
    }

    private String handleDeliveryStatus(String questionId) {
        if (deliveryLogStore == null) {
            throw new HttpApiException(404, "not_found", "No delivery log store configured");
        }
        List<DeliveryAttempt> attempts = deliveryLogStore.findByQuestion(questionId);
        return encodeDeliveryAttempts(attempts);
    }

    private String encodeDeliveryAttempts(List<DeliveryAttempt> attempts) {
        if (attempts.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < attempts.size(); i++) {
            if (i > 0) sb.append(",");
            DeliveryAttempt a = attempts.get(i);
            sb.append("{\"questionId\":").append(escapeJson(a.questionId()));
            sb.append(",\"channelType\":").append(escapeJson(a.channelType()));
            sb.append(",\"attemptNumber\":").append(a.attemptNumber());
            sb.append(",\"status\":").append(escapeJson(a.status().name()));
            sb.append(",\"statusCode\":").append(a.statusCode() != null ? a.statusCode() : "null");
            sb.append(",\"errorMessage\":").append(a.errorMessage() != null ? escapeJson(a.errorMessage()) : "null");
            sb.append(",\"attemptedAt\":").append(a.attemptedAt());
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private Map<String, String> extractCallbackHeaders(HttpServletRequest req) {
        Map<String, String> headers = new LinkedHashMap<>();
        String telegramSecret = req.getHeader("X-Telegram-Bot-Api-Secret-Token");
        if (telegramSecret != null) {
            headers.put("X-Telegram-Bot-Api-Secret-Token", telegramSecret);
        }
        String discordSignature = req.getHeader("X-Signature-256");
        if (discordSignature != null) {
            headers.put("X-Signature-256", discordSignature);
        }
        return headers;
    }

    // --- Error mapping ---

    HttpApiException mapException(Exception e) {
        if (e instanceof HttpApiException http) {
            return http;
        }
        if (e instanceof SdkLlmException) {
            return new HttpApiException(502, "llm_error", e.getMessage());
        }
        if (e instanceof SdkPermissionException) {
            return new HttpApiException(403, "permission_denied", e.getMessage());
        }
        if (e instanceof SdkToolException) {
            return new HttpApiException(422, "tool_error", e.getMessage());
        }
        if (e instanceof SdkVaultException) {
            return new HttpApiException(500, "vault_error", e.getMessage());
        }
        if (e instanceof SdkConfigException) {
            return new HttpApiException(500, "config_error", e.getMessage());
        }
        return new HttpApiException(500, "internal", e.getMessage() != null ? e.getMessage() : "Internal error");
    }

    // --- Helpers ---

    private static String escapeJson(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    private void requirePost(String method, String path) {
        if (!"POST".equals(method)) {
            throw new HttpApiException(405, "method_not_allowed", "POST required for " + path);
        }
    }

    private void requireGet(String method, String path) {
        if (!"GET".equals(method)) {
            throw new HttpApiException(405, "method_not_allowed", "GET required for " + path);
        }
    }

    private long parseNonNegativeLong(String raw, String field, long defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            long value = Long.parseLong(raw);
            if (value < 0) {
                throw new NumberFormatException("negative value");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new HttpApiException(400, "invalid_params", "Invalid query param '" + field + "'");
        }
    }

    private int parseLimit(String raw) {
        long value = parseNonNegativeLong(raw, "limit", EVENT_POLL_DEFAULT_LIMIT);
        if (value < 1 || value > EVENT_POLL_MAX_LIMIT) {
            throw new HttpApiException(400, "invalid_params", "Invalid query param 'limit'");
        }
        return (int) value;
    }

    private EventType parseEventType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return EventType.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new HttpApiException(400, "invalid_params", "Invalid query param 'type'");
        }
    }

    private void ensureEventBufferSubscribed() {
        if (sdk == null || eventBufferSubscribed) {
            return;
        }
        synchronized (this) {
            if (eventBufferSubscribed) {
                return;
            }
            for (EventType type : EventType.values()) {
                sdk.eventBus().subscribe(type, eventBuffer::record);
            }
            eventBufferSubscribed = true;
        }
    }

    private String readBody(HttpServletRequest req) {
        try (BufferedReader reader = req.getReader()) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (IOException e) {
            throw new HttpApiException(400, "invalid_params", "Failed to read request body");
        }
    }

    private String encodeEventPage(EventPollPage page) {
        StringBuilder sb = new StringBuilder("{\"events\":[");
        for (int i = 0; i < page.events().size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(page.events().get(i).toJson());
        }
        sb.append("],\"nextCursor\":").append(page.nextCursor()).append("}");
        return sb.toString();
    }

    private void sendJson(HttpServletResponse resp, int status, String json) {
        try {
            resp.setStatus(status);
            resp.setContentType("application/json; charset=utf-8");
            if (json != null) {
                resp.getWriter().write(json);
            }
        } catch (IOException e) {
            // Best-effort — response may already be committed
        }
    }

    // --- Internal types ---

    private record Route(String method, String[] segments) {
        int length() { return segments.length; }
        String segment(int i) { return i < segments.length ? segments[i] : null; }
    }

    private record EventPollPage(List<HttpEvent> events, long nextCursor) {}

    private record HttpEvent(long sequence, Event event) {
        String toJson() {
            String eventJson = event.toJson();
            return "{\"sequence\":" + sequence + "," + eventJson.substring(1);
        }
    }

    private static class HttpEventBuffer {
        private final int capacity;
        private final Deque<HttpEvent> events = new ArrayDeque<>();
        private long nextSequence;

        HttpEventBuffer(int capacity) {
            this.capacity = capacity;
        }

        synchronized void record(Event event) {
            long sequence = ++nextSequence;
            events.addLast(new HttpEvent(sequence, event));
            while (events.size() > capacity) {
                events.removeFirst();
            }
        }

        synchronized EventPollPage poll(long after, int limit, EventType type) {
            List<HttpEvent> matches = new ArrayList<>();
            long highestSeen = after;
            for (HttpEvent event : events) {
                if (event.sequence() <= after) {
                    continue;
                }
                highestSeen = Math.max(highestSeen, event.sequence());
                if (type != null && event.event().type() != type) {
                    continue;
                }
                matches.add(event);
                if (matches.size() == limit) {
                    break;
                }
            }
            long nextCursor = matches.isEmpty()
                    ? highestSeen
                    : matches.get(matches.size() - 1).sequence();
            return new EventPollPage(List.copyOf(matches), nextCursor);
        }
    }

    private static class TrackedAgent {
        final SdkAgent agent;
        final long createdAt;
        final long updatedAt;

        TrackedAgent(SdkAgent agent, long createdAt) {
            this(agent, createdAt, createdAt);
        }

        TrackedAgent(SdkAgent agent, long createdAt, long updatedAt) {
            this.agent = agent;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
    }
}
