package org.specdriven.agent.http;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.specdriven.agent.agent.AgentState;
import org.specdriven.agent.tool.Tool;
import org.specdriven.agent.tool.ToolParameter;
import org.specdriven.sdk.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST API servlet that maps HTTP requests under {@code /api/v1/*} to SDK operations.
 * Registered on Lealone's TomcatRouter with wildcard pattern.
 */
public class HttpApiServlet extends HttpServlet {

    private static final String VERSION = "0.1.0";

    private SpecDriven sdk;
    private final Map<String, TrackedAgent> agents = new ConcurrentHashMap<>();

    /** Default constructor — SDK is created in {@link #init()}. */
    public HttpApiServlet() {}

    /** Dependency-injection constructor — used when SDK is assembled externally. */
    public HttpApiServlet(SpecDriven sdk) {
        this.sdk = sdk;
    }

    @Override
    public void init() {
        if (sdk == null) {
            sdk = SpecDriven.builder().build();
        }
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) {
        try {
            Route route = parseRoute(req);
            String json = dispatch(route, req);
            sendJson(resp, 200, json);
        } catch (HttpApiException e) {
            sendJson(resp, e.httpStatus(), HttpJsonCodec.encode(e.toErrorResponse()));
        } catch (Exception e) {
            HttpApiException mapped = mapException(e);
            sendJson(resp, mapped.httpStatus(), HttpJsonCodec.encode(mapped.toErrorResponse()));
        }
    }

    // --- Route parsing ---

    private Route parseRoute(HttpServletRequest req) {
        String pathInfo = req.getPathInfo();
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
        if ("tools".equals(group) && route.length() >= 2) {
            requireGet(route.method(), "/tools");
            return handleToolsList();
        }
        if ("health".equals(group) && route.length() >= 2) {
            requireGet(route.method(), "/health");
            return handleHealth();
        }
        throw new HttpApiException(404, "not_found", "Unknown route");
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
            List<Map<String, Object>> params = new ArrayList<>();
            for (ToolParameter p : tool.getParameters()) {
                Map<String, Object> paramInfo = new LinkedHashMap<>();
                paramInfo.put("name", p.name());
                paramInfo.put("type", p.type());
                paramInfo.put("description", p.description());
                paramInfo.put("required", p.required());
                params.add(paramInfo);
            }
            toolInfos.add(new ToolInfo(tool.getName(), tool.getDescription(), params));
        }
        return HttpJsonCodec.encode(new ToolsListResponse(toolInfos));
    }

    private String handleHealth() {
        return HttpJsonCodec.encode(new HealthResponse("ok", VERSION));
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
