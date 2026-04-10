package org.specdriven.sdk;

import org.specdriven.agent.agent.*;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.event.SimpleEventBus;
import org.specdriven.agent.hook.ToolExecutionHook;
import org.specdriven.agent.question.*;
import org.specdriven.agent.tool.Tool;
import org.specdriven.agent.tool.ToolContext;
import org.specdriven.agent.tool.ToolInput;
import org.specdriven.agent.tool.ToolResult;

import java.util.*;

/**
 * Agent handle returned by the SDK. Wraps DefaultAgent lifecycle
 * and provides a simple {@code run(String)} convenience method.
 */
public class SdkAgent {

    private final SdkInternalAgent agent;
    private final Map<String, Tool> toolRegistry;
    private final SdkConfig sdkConfig;
    private final String systemPrompt;
    private final EventBus globalBus;
    private final SimpleEventBus agentBus;
    private final DeliveryMode deliveryModeOverride;
    private final QuestionDeliveryService deliveryService;

    SdkAgent(LlmProviderRegistry providerRegistry,
             Map<String, Tool> toolRegistry,
             SdkConfig sdkConfig,
             String systemPrompt,
             EventBus globalBus,
             DeliveryMode deliveryModeOverride,
             QuestionDeliveryService deliveryService) {
        this.globalBus = globalBus;
        this.agentBus = new SimpleEventBus();
        this.agent = new SdkInternalAgent(providerRegistry, globalBus, agentBus,
                deliveryModeOverride, deliveryService);
        this.toolRegistry = toolRegistry;
        this.sdkConfig = sdkConfig;
        this.systemPrompt = systemPrompt;
        this.deliveryModeOverride = deliveryModeOverride;
        this.deliveryService = deliveryService;
    }

    /**
     * Backward-compatible constructor without delivery mode override.
     */
    SdkAgent(LlmProviderRegistry providerRegistry,
             Map<String, Tool> toolRegistry,
             SdkConfig sdkConfig,
             String systemPrompt,
             EventBus globalBus) {
        this(providerRegistry, toolRegistry, sdkConfig, systemPrompt, globalBus, null, null);
    }

    /**
     * Registers a per-agent event listener that receives ALL events from this agent only.
     */
    public SdkAgent onEvent(SdkEventListener listener) {
        for (EventType type : EventType.values()) {
            agentBus.subscribe(type, listener);
        }
        return this;
    }

    /**
     * Registers a per-agent event listener for a specific event type only.
     */
    public SdkAgent onEvent(EventType type, SdkEventListener listener) {
        agentBus.subscribe(type, listener);
        return this;
    }

    /**
     * Runs the agent with the given user prompt. Manages the full lifecycle:
     * init → start → execute → stop.
     *
     * @param prompt the user message to send
     * @return the agent's text response
     * @throws SdkException if execution fails
     */
    public String run(String prompt) {
        try {
            String sessionId = UUID.randomUUID().toString();
            agent.setSessionId(sessionId);

            Conversation conversation = new Conversation();

            if (systemPrompt != null && !systemPrompt.isBlank()) {
                conversation.append(new SystemMessage(systemPrompt, System.currentTimeMillis()));
            }
            conversation.append(new UserMessage(prompt, System.currentTimeMillis()));

            Map<String, String> agentConfig = buildAgentConfig();
            agent.init(agentConfig);
            agent.start();

            AgentContext context = new SimpleAgentContext(
                    sessionId,
                    agentConfig,
                    toolRegistry,
                    conversation,
                    null,
                    null,
                    deliveryService != null ? deliveryService.runtime() : null,
                    null
            );

            try {
                agent.execute(context);
            } finally {
                if (agent.getState() != AgentState.STOPPED) {
                    agent.stop();
                }
            }

            // Extract last assistant message as response
            List<Message> history = conversation.history();
            for (int i = history.size() - 1; i >= 0; i--) {
                if (history.get(i) instanceof AssistantMessage am) {
                    return am.content();
                }
            }
            return "";
        } catch (Exception e) {
            if (e instanceof SdkException) throw (SdkException) e;
            if (isLlmError(e)) {
                throw new SdkLlmException("LLM call failed: " + e.getMessage(), e);
            }
            throw new SdkException("Agent execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Returns pending (WAITING_FOR_ANSWER) questions for a session.
     *
     * @param sessionId the agent session to query
     * @return list of pending questions, never null
     */
    public List<Question> pendingQuestions(String sessionId) {
        if (deliveryService == null) {
            return List.of();
        }
        Question pending = deliveryService.pendingQuestion(sessionId).orElse(null);
        if (pending == null) {
            return List.of();
        }
        return List.of(pending);
    }

    /**
     * Submits a human reply to a waiting question.
     *
     * @param sessionId  the session that owns the question
     * @param questionId the question being answered
     * @param answer     the human-provided answer
     * @throws SdkException if the session has no waiting question or the question is expired
     */
    public void submitHumanReply(String sessionId, String questionId, Answer answer) {
        if (deliveryService == null) {
            throw new SdkException("question delivery is not configured", false);
        }
        try {
            deliveryService.submitReply(sessionId, questionId, answer);
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new SdkException(e.getMessage(), false);
        }
    }

    /**
     * Stops the agent if currently running.
     */
    public void stop() {
        AgentState state = agent.getState();
        if (state == AgentState.RUNNING || state == AgentState.PAUSED || state == AgentState.ERROR) {
            agent.stop();
        }
    }

    /**
     * Returns the current agent state.
     */
    public AgentState getState() {
        return agent.getState();
    }

    private Map<String, String> buildAgentConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("maxTurns", String.valueOf(sdkConfig.maxTurns()));
        config.put("toolTimeoutSeconds", String.valueOf(sdkConfig.toolTimeoutSeconds()));
        return config;
    }

    private static boolean isLlmError(Throwable e) {
        Throwable current = e;
        while (current != null) {
            String className = current.getClass().getName();
            String message = current.getMessage();
            if (className.contains("OpenAi") || className.contains("Claude") || className.contains("Llm")) {
                return true;
            }
            if (message != null && message.contains("API error")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Internal subclass that provides the LLM client from the provider registry
     * and emits events during lifecycle transitions.
     */
    private static class SdkInternalAgent extends DefaultAgent {
        private final LlmProviderRegistry providerRegistry;
        private final EventBus globalBus;
        private final SimpleEventBus agentBus;
        private final DeliveryMode deliveryModeOverride;
        private final QuestionDeliveryService deliveryService;
        private String sessionId;

        SdkInternalAgent(LlmProviderRegistry providerRegistry, EventBus globalBus,
                         SimpleEventBus agentBus, DeliveryMode deliveryModeOverride,
                         QuestionDeliveryService deliveryService) {
            this.providerRegistry = providerRegistry;
            this.globalBus = globalBus;
            this.agentBus = agentBus;
            this.deliveryModeOverride = deliveryModeOverride;
            this.deliveryService = deliveryService;
        }

        void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public void init(Map<String, String> config) {
            AgentState from = getState();
            super.init(config);
            emitStateChanged(from, AgentState.IDLE);
        }

        @Override
        public void start() {
            AgentState from = getState();
            super.start();
            emitStateChanged(from, AgentState.RUNNING);
        }

        @Override
        public void stop() {
            AgentState from = getState();
            super.stop();
            emitStateChanged(from, AgentState.STOPPED);
        }

        @Override
        protected void doExecute(AgentContext context) {
            try {
                super.doExecute(context);
            } catch (Exception e) {
                emitError(e);
                throw e;
            }
        }

        @Override
        protected OrchestratorConfig buildOrchestratorConfig() {
            OrchestratorConfig base = super.buildOrchestratorConfig();
            List<ToolExecutionHook> hooks = new ArrayList<>(base.hooks());
            hooks.add(new EventEmittingToolHook(globalBus, agentBus, sessionId));
            return new OrchestratorConfig(
                    base.maxTurns(),
                    base.toolTimeoutSeconds(),
                    base.questionTimeoutSeconds(),
                    hooks);
        }

        @Override
        protected LlmClient createLlmClient(AgentContext context) {
            if (providerRegistry == null) {
                return null;
            }
            try {
                LlmProvider provider = providerRegistry.defaultProvider();
                return provider.createClient();
            } catch (Exception e) {
                return null;
            }
        }

        private void emitStateChanged(AgentState from, AgentState to) {
            if (from == null && to == AgentState.IDLE) {
                // skip init transition — not interesting to SDK users
                return;
            }
            Event event = new Event(
                    EventType.AGENT_STATE_CHANGED,
                    System.currentTimeMillis(),
                    sessionId != null ? sessionId : "unknown",
                    Map.of("fromState", from != null ? from.name() : "null", "toState", to.name())
            );
            publishEvent(event);
        }

        private void emitError(Exception e) {
            Event event = new Event(
                    EventType.ERROR,
                    System.currentTimeMillis(),
                    sessionId != null ? sessionId : "unknown",
                    Map.of("errorClass", e.getClass().getName(), "errorMessage", e.getMessage() != null ? e.getMessage() : "")
            );
            publishEvent(event);
        }

        private void publishEvent(Event event) {
            agentBus.publish(event);
            globalBus.publish(event);
        }
    }

    /**
     * ToolExecutionHook that emits TOOL_EXECUTED events after each tool invocation.
     */
    private static class EventEmittingToolHook implements ToolExecutionHook {
        private final EventBus globalBus;
        private final SimpleEventBus agentBus;
        private final String source;
        private long startTime;

        EventEmittingToolHook(EventBus globalBus, SimpleEventBus agentBus, String source) {
            this.globalBus = globalBus;
            this.agentBus = agentBus;
            this.source = source;
        }

        @Override
        public ToolResult beforeExecute(Tool tool, ToolInput input, ToolContext context) {
            startTime = System.currentTimeMillis();
            return null;
        }

        @Override
        public void afterExecute(Tool tool, ToolInput input, ToolResult result) {
            long durationMs = System.currentTimeMillis() - startTime;
            boolean success = result instanceof ToolResult.Success;
            Event event = new Event(
                    EventType.TOOL_EXECUTED,
                    System.currentTimeMillis(),
                    source != null ? source : "unknown",
                    Map.of("toolName", tool.getName(), "success", success, "durationMs", durationMs)
            );
            agentBus.publish(event);
            globalBus.publish(event);
        }
    }
}
