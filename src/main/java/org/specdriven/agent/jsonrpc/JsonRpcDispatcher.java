package org.specdriven.agent.jsonrpc;

import org.specdriven.agent.agent.AgentState;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.question.*;
import org.specdriven.agent.tool.Tool;
import org.specdriven.agent.tool.ToolParameter;
import org.specdriven.agent.registry.TaskStore;
import org.specdriven.agent.registry.TeamStore;
import org.specdriven.agent.registry.LealoneTaskStore;
import org.specdriven.agent.registry.LealoneTeamStore;
import org.specdriven.agent.registry.Task;
import org.specdriven.agent.registry.TaskStatus;
import org.specdriven.agent.registry.Team;
import org.specdriven.agent.registry.TeamMember;
import org.specdriven.agent.tool.ProcessManager;
import org.specdriven.agent.tool.DefaultProcessManager;
import org.specdriven.agent.tool.BackgroundProcessHandle;
import org.specdriven.agent.tool.ProcessState;
import org.specdriven.agent.tool.ProcessOutput;
import org.specdriven.sdk.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Implements {@link JsonRpcMessageHandler} to route inbound JSON-RPC 2.0
 * requests to SDK operations and forward agent events as notifications.
 */
public class JsonRpcDispatcher implements JsonRpcMessageHandler {

    private static final String VERSION = "0.1.0";

    private final JsonRpcTransport transport;
    private final ExecutorService executor;

    private volatile SpecDriven sdk;
    private volatile boolean shutdown;
    private final Map<Object, SdkAgent> activeAgents = new ConcurrentHashMap<>();
    private final ProcessManager processManager;
    private volatile TaskStore taskStore;
    private volatile TeamStore teamStore;

    public JsonRpcDispatcher(JsonRpcTransport transport) {
        this.transport = transport;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.processManager = new DefaultProcessManager(new org.specdriven.agent.event.SimpleEventBus());
    }

    // --- JsonRpcMessageHandler ---

    @Override
    public void onRequest(JsonRpcRequest request) {
        try {
            switch (request.method()) {
                case "initialize" -> handleInitialize(request);
                case "shutdown" -> handleShutdown(request);
                case "agent/run" -> handleAgentRun(request);
                case "agent/stop" -> handleAgentStop(request);
                case "agent/state" -> handleAgentState(request);
                case "tools/list" -> handleToolsList(request);
                case "question/answer" -> handleQuestionAnswer(request);
                case "workflow/start" -> handleWorkflowStart(request);
                case "workflow/state" -> handleWorkflowState(request);
                case "workflow/result" -> handleWorkflowResult(request);
                case "tasks/list" -> handleTasksList(request);
                case "tasks/stop" -> handleTasksStop(request);
                case "tasks/state" -> handleTasksState(request);
                case "tasks/output" -> handleTasksOutput(request);
                case "registry/tasks" -> handleRegistryTasks(request);
                case "registry/teams" -> handleRegistryTeams(request);
                case "registry/team-members" -> handleRegistryTeamMembers(request);
                default -> sendError(request.id(), JsonRpcError.methodNotFound());
            }
        } catch (Exception e) {
            sendError(request.id(), mapException(e));
        }
    }

    @Override
    public void onNotification(JsonRpcNotification notification) {
        if ("$/cancel".equals(notification.method())) {
            handleCancel(notification);
        }
    }

    @Override
    public void onError(Throwable error) {
        // Best-effort: try to notify the client about the transport error
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("type", "transportError");
            params.put("message", error.getMessage() != null ? error.getMessage() : error.getClass().getName());
            transport.send(new JsonRpcNotification("event", params));
        } catch (Exception ignored) {
            // Transport may be broken — nothing we can do
        }
    }

    // --- Handlers ---

    private void handleInitialize(JsonRpcRequest request) {
        if (sdk != null) {
            sendError(request.id(), JsonRpcError.invalidRequest());
            return;
        }

        SdkBuilder builder = SpecDriven.builder();

        Object params = request.params();
        if (params instanceof Map<?, ?> map) {
            Object configPath = map.get("configPath");
            if (configPath instanceof String cp && !cp.isBlank()) {
                builder.config(Path.of(cp));
            }
            Object systemPrompt = map.get("systemPrompt");
            if (systemPrompt instanceof String sp && !sp.isBlank()) {
                builder.systemPrompt(sp);
            }
        }

        // Register event forwarding listener
        builder.onEvent((SdkEventListener) this::forwardEvent);

        this.sdk = builder.build();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", VERSION);
        result.put("capabilities", Map.of(
                "methods", List.of("initialize", "shutdown", "agent/run", "agent/stop", "agent/state", "tools/list", "question/answer", "workflow/start", "workflow/state", "workflow/result", "tasks/list", "tasks/stop", "tasks/state", "tasks/output", "registry/tasks", "registry/teams", "registry/team-members"),
                "notifications", List.of("$/cancel", "event")
        ));
        sendSuccess(request.id(), result);
    }

    private void handleShutdown(JsonRpcRequest request) {
        if (sdk != null) {
            try {
                sdk.close();
            } catch (Exception ignored) {
                // best-effort
            }
        }
        shutdown = true;
        executor.shutdownNow();
        sendSuccess(request.id(), null);
    }

    private void handleAgentRun(JsonRpcRequest request) {
        if (requireInitialized(request.id())) return;

        String prompt = extractStringParam(request, "prompt");
        if (prompt == null) {
            sendError(request.id(), JsonRpcError.invalidParams());
            return;
        }

        SdkAgent agent = sdk.createAgent();
        activeAgents.put(request.id(), agent);

        executor.submit(() -> {
            try {
                String output = agent.run(prompt);
                sendSuccess(request.id(), Map.of("output", output != null ? output : ""));
            } catch (Exception e) {
                sendError(request.id(), mapException(e));
            } finally {
                activeAgents.remove(request.id());
            }
        });
    }

    private void handleAgentStop(JsonRpcRequest request) {
        if (requireInitialized(request.id())) return;

        // Stop all active agents (single-agent-per-call model)
        for (SdkAgent agent : activeAgents.values()) {
            try {
                agent.stop();
            } catch (Exception ignored) {
                // best-effort
            }
        }
        sendSuccess(request.id(), null);
    }

    private void handleAgentState(JsonRpcRequest request) {
        if (requireInitialized(request.id())) return;

        // Return state of the most recently active agent, or IDLE if none
        AgentState state = AgentState.IDLE;
        for (SdkAgent agent : activeAgents.values()) {
            AgentState s = agent.getState();
            if (s == AgentState.RUNNING) {
                state = s;
                break;
            }
            state = s;
        }
        sendSuccess(request.id(), Map.of("state", state.name()));
    }

    private void handleToolsList(JsonRpcRequest request) {
        if (requireInitialized(request.id())) return;

        List<Map<String, Object>> toolList = new ArrayList<>();
        for (Tool tool : sdk.tools()) {
            Map<String, Object> toolInfo = new LinkedHashMap<>();
            toolInfo.put("name", tool.getName());
            toolInfo.put("description", tool.getDescription());
            List<Map<String, Object>> params = new ArrayList<>();
            for (ToolParameter p : tool.getParameters()) {
                Map<String, Object> paramInfo = new LinkedHashMap<>();
                paramInfo.put("name", p.name());
                paramInfo.put("type", p.type());
                paramInfo.put("description", p.description());
                paramInfo.put("required", p.required());
                params.add(paramInfo);
            }
            toolInfo.put("parameters", params);
            toolList.add(toolInfo);
        }
        sendSuccess(request.id(), Map.of("tools", toolList));
    }

    private void handleCancel(JsonRpcNotification notification) {
        Object params = notification.params();
        if (!(params instanceof Map<?, ?> map)) return;

        Object id = map.get("id");
        if (id == null) return;

        SdkAgent agent = activeAgents.remove(id);
        if (agent != null) {
            try {
                agent.stop();
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }

    private void handleQuestionAnswer(JsonRpcRequest request) {
        if (requireInitialized(request.id())) return;

        QuestionDeliveryService service = sdk.deliveryService();
        if (service == null) {
            sendError(request.id(), new JsonRpcError(-32603, "Question service not available", null));
            return;
        }

        Object params = request.params();
        if (!(params instanceof Map<?, ?> map)) {
            sendError(request.id(), JsonRpcError.invalidParams());
            return;
        }

        String sessionId = map.get("sessionId") instanceof String s ? s : null;
        String questionId = map.get("questionId") instanceof String s ? s : null;
        Boolean approved = map.get("approved") instanceof Boolean b ? b : null;

        if (sessionId == null || questionId == null || approved == null) {
            sendError(request.id(), JsonRpcError.invalidParams());
            return;
        }

        Optional<Question> pending = service.pendingQuestion(sessionId);
        if (pending.isEmpty()) {
            sendError(request.id(), new JsonRpcError(-32603, "No waiting question found for session", null));
            return;
        }

        Question question = pending.get();
        if (!question.questionId().equals(questionId)) {
            sendError(request.id(), new JsonRpcError(-32603, "Question not found or expired", null));
            return;
        }

        if (question.deliveryMode() != DeliveryMode.PAUSE_WAIT_HUMAN) {
            sendError(request.id(), new JsonRpcError(-32603,
                    "Unsupported delivery mode: " + question.deliveryMode(), null));
            return;
        }

        QuestionDecision decision = approved ? QuestionDecision.ANSWER_ACCEPTED : QuestionDecision.CANCELLED;
        String content = approved ? "Approved" : "Rejected";
        Answer answer = new Answer(
                content,
                "Human inline response via JSON-RPC",
                "json-rpc",
                AnswerSource.HUMAN_INLINE,
                1.0,
                decision,
                question.deliveryMode(),
                "Human inline response via JSON-RPC question/answer",
                System.currentTimeMillis()
        );

        service.submitReply(sessionId, questionId, answer);
        sendSuccess(request.id(), Map.of("status", "accepted"));
    }

    private void handleWorkflowStart(JsonRpcRequest request) {
        if (requireInitialized(request.id())) return;
        Object params = request.params();
        if (!(params instanceof Map<?, ?> map)) {
            sendError(request.id(), JsonRpcError.invalidParams());
            return;
        }
        Object workflowNameRaw = map.get("workflowName");
        if (!(workflowNameRaw instanceof String workflowName) || workflowName.isBlank()) {
            sendError(request.id(), JsonRpcError.invalidParams());
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> input = map.get("input") instanceof Map<?, ?> inputMap
                ? (Map<String, Object>) inputMap
                : Map.of();
        try {
            WorkflowInstanceView view = sdk.startWorkflow(workflowName, input);
            sendSuccess(request.id(), workflowInstanceResult(view));
        } catch (IllegalArgumentException e) {
            sendError(request.id(), new JsonRpcError(-32603, e.getMessage(), null));
        }
    }

    private void handleWorkflowState(JsonRpcRequest request) {
        if (requireInitialized(request.id())) return;
        String workflowId = extractStringParam(request, "workflowId");
        if (workflowId == null || workflowId.isBlank()) {
            sendError(request.id(), JsonRpcError.invalidParams());
            return;
        }
        try {
            WorkflowInstanceView view = sdk.workflowState(workflowId);
            sendSuccess(request.id(), workflowInstanceResult(view));
        } catch (IllegalArgumentException e) {
            sendError(request.id(), new JsonRpcError(-32603, e.getMessage(), null));
        }
    }

    private void handleWorkflowResult(JsonRpcRequest request) {
        if (requireInitialized(request.id())) return;
        String workflowId = extractStringParam(request, "workflowId");
        if (workflowId == null || workflowId.isBlank()) {
            sendError(request.id(), JsonRpcError.invalidParams());
            return;
        }
        try {
            WorkflowResultView view = sdk.workflowResult(workflowId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("workflowId", view.workflowId());
            result.put("workflowName", view.workflowName());
            result.put("status", view.status().name());
            result.put("result", view.result());
            result.put("failureSummary", view.failureSummary());
            result.put("createdAt", view.createdAt());
            result.put("updatedAt", view.updatedAt());
            sendSuccess(request.id(), result);
        } catch (IllegalArgumentException e) {
            sendError(request.id(), new JsonRpcError(-32603, e.getMessage(), null));
        }
    }

    // --- Background task handlers ---

    private void handleTasksList(JsonRpcRequest request) {
        if (requireInitialized(request.id())) return;

        List<BackgroundProcessHandle> active = processManager.listActive();
        List<Map<String, Object>> result = new ArrayList<>();
        for (BackgroundProcessHandle handle : active) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", handle.id());
            item.put("pid", handle.pid());
            item.put("command", handle.command());
            item.put("toolName", handle.toolName());
            item.put("startTime", handle.startTime());
            item.put("state", handle.state().name());
            result.add(item);
        }
        sendSuccess(request.id(), Map.of("tasks", result));
    }

    private void handleTasksStop(JsonRpcRequest request) {
        if (requireInitialized(request.id())) return;

        String processId = extractStringParam(request, "processId");
        if (processId == null || processId.isBlank()) {
            sendError(request.id(), JsonRpcError.invalidParams());
            return;
        }

        boolean stopped = processManager.stop(processId);
        sendSuccess(request.id(), Map.of("success", stopped));
    }

    private void handleTasksState(JsonRpcRequest request) {
        if (requireInitialized(request.id())) return;

        String processId = extractStringParam(request, "processId");
        if (processId == null || processId.isBlank()) {
            sendError(request.id(), JsonRpcError.invalidParams());
            return;
        }

        Optional<ProcessState> state = processManager.getState(processId);
        if (state.isEmpty()) {
            sendError(request.id(), new JsonRpcError(-32602, "Process not found: " + processId, null));
            return;
        }
        sendSuccess(request.id(), Map.of("state", state.get().name()));
    }

    private void handleTasksOutput(JsonRpcRequest request) {
        if (requireInitialized(request.id())) return;

        String processId = extractStringParam(request, "processId");
        if (processId == null || processId.isBlank()) {
            sendError(request.id(), JsonRpcError.invalidParams());
            return;
        }

        Optional<ProcessOutput> output = processManager.getOutput(processId);
        if (output.isEmpty()) {
            sendError(request.id(), new JsonRpcError(-32602, "Process not found: " + processId, null));
            return;
        }
        ProcessOutput po = output.get();
        sendSuccess(request.id(), Map.of(
                "stdout", po.stdout(),
                "stderr", po.stderr()
        ));
    }

    // --- Registry handlers ---

    private void ensureRegistryStores() {
        if (taskStore == null) {
            synchronized (this) {
                if (taskStore == null) {
                    String jdbcUrl = sdk.platform().database().jdbcUrl();
                    var bus = sdk.eventBus();
                    taskStore = new LealoneTaskStore(bus, jdbcUrl);
                    teamStore = new LealoneTeamStore(bus, jdbcUrl);
                }
            }
        }
    }

    private void handleRegistryTasks(JsonRpcRequest request) {
        if (requireInitialized(request.id())) return;
        ensureRegistryStores();

        String statusFilter = extractStringParam(request, "status");
        List<Task> tasks;
        if (statusFilter != null && !statusFilter.isBlank()) {
            try {
                TaskStatus status = TaskStatus.valueOf(statusFilter);
                tasks = taskStore.queryByStatus(status);
            } catch (IllegalArgumentException e) {
                sendError(request.id(), new JsonRpcError(-32602, "Invalid status filter: " + statusFilter, null));
                return;
            }
        } else {
            tasks = taskStore.list();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Task task : tasks) {
            result.add(taskToMap(task));
        }
        sendSuccess(request.id(), Map.of("tasks", result));
    }

    private void handleRegistryTeams(JsonRpcRequest request) {
        if (requireInitialized(request.id())) return;
        ensureRegistryStores();

        List<Team> teams = teamStore.list();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Team team : teams) {
            result.add(teamToMap(team));
        }
        sendSuccess(request.id(), Map.of("teams", result));
    }

    private void handleRegistryTeamMembers(JsonRpcRequest request) {
        if (requireInitialized(request.id())) return;
        ensureRegistryStores();

        String teamId = extractStringParam(request, "teamId");
        if (teamId == null || teamId.isBlank()) {
            sendError(request.id(), JsonRpcError.invalidParams());
            return;
        }

        List<TeamMember> members = teamStore.listMembers(teamId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (TeamMember member : members) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("teamId", member.teamId());
            item.put("memberId", member.memberId());
            item.put("role", member.role().name());
            item.put("joinedAt", member.joinedAt());
            result.add(item);
        }
        sendSuccess(request.id(), Map.of("members", result));
    }

    private Map<String, Object> taskToMap(Task task) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", task.id());
        item.put("title", task.title());
        item.put("description", task.description());
        item.put("status", task.status().name());
        item.put("owner", task.owner());
        item.put("parentTaskId", task.parentTaskId());
        item.put("metadata", task.metadata());
        item.put("createdAt", task.createdAt());
        item.put("updatedAt", task.updatedAt());
        return item;
    }

    private Map<String, Object> teamToMap(Team team) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", team.id());
        item.put("name", team.name());
        item.put("description", team.description());
        item.put("status", team.status().name());
        item.put("metadata", team.metadata());
        item.put("createdAt", team.createdAt());
        item.put("updatedAt", team.updatedAt());
        return item;
    }

    // --- Event forwarding ---

    private void forwardEvent(Event event) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("type", event.type().name());
            params.put("source", event.source());
            params.put("timestamp", event.timestamp());
            params.put("metadata", event.metadata());
            transport.send(new JsonRpcNotification("event", params));
        } catch (Exception ignored) {
            // Transport may be closed — nothing we can do
        }
    }

    // --- Error mapping ---

    // Package-private for testing
    JsonRpcError mapException(Exception e) {
        if (e instanceof JsonRpcProtocolException proto) {
            return new JsonRpcError(proto.getErrorCode(), proto.getMessage(), null);
        }
        if (e instanceof SdkLlmException) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("retryable", true);
            return new JsonRpcError(-32603, e.getMessage(), data);
        }
        if (e instanceof SdkPermissionException) {
            return new JsonRpcError(-32600, e.getMessage(), null);
        }
        if (e instanceof SdkToolException) {
            return new JsonRpcError(-32602, e.getMessage(), null);
        }
        if (e instanceof SdkVaultException) {
            return new JsonRpcError(-32603, e.getMessage(), null);
        }
        if (e instanceof SdkConfigException) {
            return new JsonRpcError(-32603, e.getMessage(), null);
        }
        // Default: internal error
        return new JsonRpcError(-32603,
                e.getMessage() != null ? e.getMessage() : "Internal error", null);
    }

    // --- Helpers ---

    SpecDriven sdk() {
        return sdk;
    }

    private boolean requireInitialized(Object requestId) {
        if (sdk == null || shutdown) {
            sendError(requestId, JsonRpcError.invalidRequest());
            return true;
        }
        return false;
    }

    private String extractStringParam(JsonRpcRequest request, String name) {
        Object params = request.params();
        if (!(params instanceof Map<?, ?> map)) return null;
        Object value = map.get(name);
        return value instanceof String s ? s : null;
    }

    private Map<String, Object> workflowInstanceResult(WorkflowInstanceView view) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workflowId", view.workflowId());
        result.put("workflowName", view.workflowName());
        result.put("status", view.status().name());
        result.put("createdAt", view.createdAt());
        result.put("updatedAt", view.updatedAt());
        return result;
    }

    private void sendSuccess(Object id, Object result) {
        try {
            transport.send(JsonRpcResponse.success(id, result));
        } catch (Exception ignored) {
            // Transport may be closed
        }
    }

    private void sendError(Object id, JsonRpcError error) {
        try {
            transport.send(JsonRpcResponse.error(id, error));
        } catch (Exception ignored) {
            // Transport may be closed
        }
    }
}
