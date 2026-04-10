package org.specdriven.agent.agent;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.event.SimpleEventBus;
import org.specdriven.agent.hook.*;
import org.specdriven.agent.permission.AuditEntry;
import org.specdriven.agent.permission.Permission;
import org.specdriven.agent.permission.PermissionContext;
import org.specdriven.agent.permission.PermissionDecision;
import org.specdriven.agent.permission.PolicyStore;
import org.specdriven.agent.permission.StoredPolicy;
import org.specdriven.agent.question.Answer;
import org.specdriven.agent.question.AnswerSource;
import org.specdriven.agent.question.DeliveryMode;
import org.specdriven.agent.question.Question;
import org.specdriven.agent.question.QuestionCategory;
import org.specdriven.agent.question.QuestionDecision;
import org.specdriven.agent.question.QuestionRuntime;
import org.specdriven.agent.tool.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class DefaultOrchestratorTest {

    // --- helpers ---

    private static Tool stubTool(String name, String output) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return "stub"; }
            @Override public List<ToolParameter> getParameters() { return List.of(); }
            @Override public ToolResult execute(ToolInput input, ToolContext ctx) {
                return new ToolResult.Success(output);
            }
        };
    }

    private static Tool failingTool(String name, String errorMsg) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return "failing stub"; }
            @Override public List<ToolParameter> getParameters() { return List.of(); }
            @Override public ToolResult execute(ToolInput input, ToolContext ctx) {
                return new ToolResult.Error(errorMsg);
            }
        };
    }

    private static AgentContext ctx(Map<String, Tool> tools, Conversation conv) {
        return new AgentContext() {
            @Override public String sessionId() { return "test"; }
            @Override public Map<String, String> config() { return Map.of("workDir", "."); }
            @Override public Map<String, Tool> toolRegistry() { return tools; }
            @Override public Conversation conversation() { return conv; }
        };
    }

    private static SimpleAgentContext questionCtx(Map<String, String> config,
                                                  Conversation conv,
                                                  QuestionRuntime questionRuntime) {
        return new SimpleAgentContext(
                "test-session",
                config,
                Map.of(),
                conv,
                null,
                null,
                questionRuntime
        );
    }

    private static PolicyStore allowingStore() {
        return new PolicyStore() {
            @Override public void grant(Permission permission, PermissionContext context) {}
            @Override public void revoke(Permission permission, PermissionContext context) {}
            @Override public Optional<PermissionDecision> find(Permission permission, PermissionContext context) {
                return Optional.of(PermissionDecision.ALLOW);
            }
            @Override public List<StoredPolicy> listPolicies() { return List.of(); }
            @Override public List<AuditEntry> auditLog() { return List.of(); }
        };
    }

    // --- tests ---

    @Test
    void textResponseTerminatesLoop() {
        Conversation conv = new Conversation();
        LlmClient llm = msgs -> new LlmResponse.TextResponse("done");

        Orchestrator orch = new DefaultOrchestrator(
                OrchestratorConfig.defaults(), () -> AgentState.RUNNING);
        orch.run(ctx(Map.of(), conv), llm);

        assertEquals(1, conv.size());
        assertInstanceOf(AssistantMessage.class, conv.get(0));
        assertEquals("done", ((AssistantMessage) conv.get(0)).content());
    }

    @Test
    void textOnlyRun_doesNotInitializePolicyStore() {
        Conversation conv = new Conversation();
        AtomicInteger initCount = new AtomicInteger(0);
        Supplier<PolicyStore> factory = () -> {
            initCount.incrementAndGet();
            return allowingStore();
        };

        Orchestrator orch = new DefaultOrchestrator(
                OrchestratorConfig.defaults(), () -> AgentState.RUNNING, factory);
        orch.run(ctx(Map.of(), conv), msgs -> new LlmResponse.TextResponse("done"));

        assertEquals(0, initCount.get(), "Policy store should not initialize when no tool execution occurs");
        assertEquals(1, conv.size());
    }

    @Test
    void emptyToolCallResponse_doesNotInitializePolicyStore() {
        Conversation conv = new Conversation();
        AtomicInteger initCount = new AtomicInteger(0);
        Supplier<PolicyStore> factory = () -> {
            initCount.incrementAndGet();
            return allowingStore();
        };
        OrchestratorConfig config = new OrchestratorConfig(1, 10, List.of(new PermissionCheckHook()));

        Orchestrator orch = new DefaultOrchestrator(config, () -> AgentState.RUNNING, factory);
        orch.run(ctx(Map.of(), conv), msgs -> new LlmResponse.ToolCallResponse(List.of()));

        assertEquals(0, initCount.get(), "Policy store should not initialize when tool call list is empty");
        assertEquals(1, conv.size());
        assertInstanceOf(AssistantMessage.class, conv.get(0));
    }

    @Test
    void permissionStoreInitializesLazilyOnFirstToolExecution() {
        Conversation conv = new Conversation();
        AtomicInteger initCount = new AtomicInteger(0);
        Supplier<PolicyStore> factory = () -> {
            initCount.incrementAndGet();
            return allowingStore();
        };
        Tool bash = stubTool("bash", "file.txt");
        LlmClient llm = new LlmClient() {
            private int callCount = 0;
            @Override public LlmResponse chat(List<Message> msgs) {
                callCount++;
                if (callCount == 1) {
                    return new LlmResponse.ToolCallResponse(
                            List.of(new ToolCall("bash", Map.of("command", "ls"), null)));
                }
                return new LlmResponse.TextResponse("all done");
            }
        };
        OrchestratorConfig config = new OrchestratorConfig(5, 10, List.of(new PermissionCheckHook()));

        Orchestrator orch = new DefaultOrchestrator(config, () -> AgentState.RUNNING, factory);
        orch.run(ctx(Map.of("bash", bash), conv), llm);

        assertEquals(1, initCount.get(), "Policy store should initialize exactly once on first permission check");
        assertEquals("file.txt", ((ToolMessage) conv.get(1)).content());
    }

    @Test
    void toolCallExecutedAndResultRecorded() {
        Conversation conv = new Conversation();
        Tool bash = stubTool("bash", "file.txt");
        // first call: return tool call, second call: return text
        LlmClient llm = new LlmClient() {
            private int callCount = 0;
            @Override public LlmResponse chat(List<Message> msgs) {
                callCount++;
                if (callCount == 1) {
                    return new LlmResponse.ToolCallResponse(
                            List.of(new ToolCall("bash", Map.of("command", "ls"), null)));
                }
                return new LlmResponse.TextResponse("all done");
            }
        };

        Orchestrator orch = new DefaultOrchestrator(
                OrchestratorConfig.defaults(), () -> AgentState.RUNNING);
        orch.run(ctx(Map.of("bash", bash), conv), llm);

        // assistant message (tool call intent) + tool message + assistant message (final text)
        assertEquals(3, conv.size());
        assertInstanceOf(AssistantMessage.class, conv.get(0));
        assertInstanceOf(ToolMessage.class, conv.get(1));
        assertEquals("file.txt", ((ToolMessage) conv.get(1)).content());
        assertInstanceOf(AssistantMessage.class, conv.get(2));
    }

    @Test
    void multipleToolCallsExecutedSequentially() {
        Conversation conv = new Conversation();
        List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());
        Tool toolA = new Tool() {
            @Override public String getName() { return "a"; }
            @Override public String getDescription() { return ""; }
            @Override public List<ToolParameter> getParameters() { return List.of(); }
            @Override public ToolResult execute(ToolInput input, ToolContext ctx) {
                executionOrder.add("a");
                return new ToolResult.Success("result-a");
            }
        };
        Tool toolB = new Tool() {
            @Override public String getName() { return "b"; }
            @Override public String getDescription() { return ""; }
            @Override public List<ToolParameter> getParameters() { return List.of(); }
            @Override public ToolResult execute(ToolInput input, ToolContext ctx) {
                executionOrder.add("b");
                return new ToolResult.Success("result-b");
            }
        };

        LlmClient llm = new LlmClient() {
            private int callCount = 0;
            @Override public LlmResponse chat(List<Message> msgs) {
                callCount++;
                if (callCount == 1) {
                    return new LlmResponse.ToolCallResponse(List.of(
                            new ToolCall("a", Map.of(), null),
                            new ToolCall("b", Map.of(), null)));
                }
                return new LlmResponse.TextResponse("done");
            }
        };

        Map<String, Tool> tools = Map.of("a", toolA, "b", toolB);
        Orchestrator orch = new DefaultOrchestrator(
                OrchestratorConfig.defaults(), () -> AgentState.RUNNING);
        orch.run(ctx(tools, conv), llm);

        assertEquals(List.of("a", "b"), executionOrder);
        // assistant (intent) + tool-a + tool-b + assistant (final)
        assertEquals(4, conv.size());
    }

    @Test
    void toolErrorFedBackToLlm() {
        Conversation conv = new Conversation();
        Tool failTool = failingTool("bash", "permission denied");

        LlmClient llm = new LlmClient() {
            private int callCount = 0;
            @Override public LlmResponse chat(List<Message> msgs) {
                callCount++;
                if (callCount == 1) {
                    return new LlmResponse.ToolCallResponse(
                            List.of(new ToolCall("bash", Map.of("command", "rm -rf /"), null)));
                }
                // second call sees the error message in conversation
                return new LlmResponse.TextResponse("I see the error, stopping");
            }
        };

        Orchestrator orch = new DefaultOrchestrator(
                OrchestratorConfig.defaults(), () -> AgentState.RUNNING);
        orch.run(ctx(Map.of("bash", failTool), conv), llm);

        // assistant + tool-error + assistant
        assertEquals(3, conv.size());
        ToolMessage toolMsg = (ToolMessage) conv.get(1);
        assertTrue(toolMsg.content().contains("permission denied"));
    }

    @Test
    void maxTurnsSafetyValve() {
        Conversation conv = new Conversation();
        // LLM always returns tool calls — would loop forever
        LlmClient llm = msgs -> new LlmResponse.ToolCallResponse(
                List.of(new ToolCall("bash", Map.of("command", "echo"), null)));
        Tool bash = stubTool("bash", "echo");

        OrchestratorConfig smallConfig = new OrchestratorConfig(3, 10);
        Orchestrator orch = new DefaultOrchestrator(smallConfig, () -> AgentState.RUNNING);
        orch.run(ctx(Map.of("bash", bash), conv), llm);

        // 3 turns × (1 assistant + 1 tool) = 6 messages
        assertEquals(6, conv.size());
    }

    @Test
    void agentNotRunningStopsLoop() {
        Conversation conv = new Conversation();
        AtomicInteger stateCheckCount = new AtomicInteger(0);
        // Agent stops after first state check returns RUNNING, then returns STOPPED
        AgentState[] states = {AgentState.RUNNING, AgentState.STOPPED};
        DefaultOrchestrator.AgentStateAccessor accessor = () -> {
            int idx = Math.min(stateCheckCount.getAndIncrement(), states.length - 1);
            return states[idx];
        };

        LlmClient llm = msgs -> new LlmResponse.ToolCallResponse(
                List.of(new ToolCall("bash", Map.of(), null)));
        Tool bash = stubTool("bash", "out");

        Orchestrator orch = new DefaultOrchestrator(OrchestratorConfig.defaults(), accessor);
        orch.run(ctx(Map.of("bash", bash), conv), llm);

        // stopped before processing — at most 1 assistant + 0 or 1 tool
        assertTrue(conv.size() <= 2);
    }

    @Test
    void toolNotFoundProducesErrorToolMessage() {
        Conversation conv = new Conversation();

        LlmClient llm = new LlmClient() {
            private int callCount = 0;
            @Override public LlmResponse chat(List<Message> msgs) {
                callCount++;
                if (callCount == 1) {
                    return new LlmResponse.ToolCallResponse(
                            List.of(new ToolCall("nonexistent", Map.of(), null)));
                }
                return new LlmResponse.TextResponse("ok");
            }
        };

        Orchestrator orch = new DefaultOrchestrator(
                OrchestratorConfig.defaults(), () -> AgentState.RUNNING);
        orch.run(ctx(Map.of(), conv), llm);

        ToolMessage toolMsg = (ToolMessage) conv.get(1);
        assertTrue(toolMsg.content().contains("tool not found"));
    }

    @Test
    void nullConversationReturnsImmediately() {
        Orchestrator orch = new DefaultOrchestrator(
                OrchestratorConfig.defaults(), () -> AgentState.RUNNING);
        AgentContext nullConvCtx = new AgentContext() {
            @Override public String sessionId() { return "test"; }
            @Override public Map<String, String> config() { return Map.of(); }
            @Override public Map<java.lang.String, Tool> toolRegistry() { return Map.of(); }
            @Override public Conversation conversation() { return null; }
        };
        // should not throw
        orch.run(nullConvCtx, msgs -> new LlmResponse.TextResponse("hi"));
    }

    // --- Hook integration tests ---

    @Test
    void hookBlocksExecution_toolNotCalled() {
        Conversation conv = new Conversation();
        AtomicInteger executeCount = new AtomicInteger(0);
        Tool tool = new Tool() {
            @Override public String getName() { return "bash"; }
            @Override public String getDescription() { return ""; }
            @Override public List<ToolParameter> getParameters() { return List.of(); }
            @Override public ToolResult execute(ToolInput input, ToolContext ctx) {
                executeCount.incrementAndGet();
                return new ToolResult.Success("should not reach");
            }
        };

        ToolExecutionHook blockingHook = new ToolExecutionHook() {
            @Override public ToolResult beforeExecute(Tool t, ToolInput i, ToolContext c) {
                return new ToolResult.Error("blocked by hook");
            }
            @Override public void afterExecute(Tool t, ToolInput i, ToolResult r) {}
        };

        OrchestratorConfig config = new OrchestratorConfig(5, 10, List.of(blockingHook));
        LlmClient llm = new LlmClient() {
            private int callCount = 0;
            @Override public LlmResponse chat(List<Message> msgs) {
                callCount++;
                if (callCount == 1) {
                    return new LlmResponse.ToolCallResponse(
                            List.of(new ToolCall("bash", Map.of(), null)));
                }
                return new LlmResponse.TextResponse("done");
            }
        };

        Orchestrator orch = new DefaultOrchestrator(config, () -> AgentState.RUNNING);
        orch.run(ctx(Map.of("bash", tool), conv), llm);

        assertEquals(0, executeCount.get(), "Tool should not be executed when hook blocks");
        ToolMessage toolMsg = (ToolMessage) conv.get(1);
        assertTrue(toolMsg.content().contains("blocked by hook"));
    }

    @Test
    void hookAllowsExecution_toolRunsNormally() {
        Conversation conv = new Conversation();
        AtomicInteger afterExecuteCount = new AtomicInteger(0);

        ToolExecutionHook allowingHook = new ToolExecutionHook() {
            @Override public ToolResult beforeExecute(Tool t, ToolInput i, ToolContext c) {
                return null; // allow
            }
            @Override public void afterExecute(Tool t, ToolInput i, ToolResult r) {
                afterExecuteCount.incrementAndGet();
            }
        };

        Tool bash = stubTool("bash", "hello");

        OrchestratorConfig config = new OrchestratorConfig(5, 10, List.of(allowingHook));
        LlmClient llm = new LlmClient() {
            private int callCount = 0;
            @Override public LlmResponse chat(List<Message> msgs) {
                callCount++;
                if (callCount == 1) {
                    return new LlmResponse.ToolCallResponse(
                            List.of(new ToolCall("bash", Map.of(), null)));
                }
                return new LlmResponse.TextResponse("done");
            }
        };

        Orchestrator orch = new DefaultOrchestrator(config, () -> AgentState.RUNNING);
        orch.run(ctx(Map.of("bash", bash), conv), llm);

        assertEquals(1, afterExecuteCount.get(), "afterExecute should be called once");
        ToolMessage toolMsg = (ToolMessage) conv.get(1);
        assertEquals("hello", toolMsg.content());
    }

    @Test
    void questionToolPausesThenResumesNextTurn() throws Exception {
        Conversation conv = new Conversation();
        SimpleEventBus eventBus = new SimpleEventBus();
        List<Event> events = Collections.synchronizedList(new ArrayList<>());
        eventBus.subscribe(EventType.QUESTION_CREATED, events::add);
        eventBus.subscribe(EventType.QUESTION_ANSWERED, events::add);

        QuestionRuntime questionRuntime = new QuestionRuntime(eventBus);
        SimpleAgentContext context = questionCtx(
                Map.of("workDir", ".", "questionTimeoutSeconds", "2"),
                conv,
                questionRuntime
        );

        AtomicInteger llmCalls = new AtomicInteger(0);
        LlmClient llm = msgs -> {
            if (llmCalls.incrementAndGet() == 1) {
                return new LlmResponse.ToolCallResponse(List.of(new ToolCall(
                        DefaultOrchestrator.QUESTION_TOOL_NAME,
                        Map.of(
                                "question", "Should we deploy now?",
                                "impact", "A bad deploy could break production.",
                                "recommendation", "Wait for human approval.",
                                "category", QuestionCategory.IRREVERSIBLE_APPROVAL.name(),
                                "deliveryMode", DeliveryMode.PAUSE_WAIT_HUMAN.name()
                        ),
                        "call-q1"
                )));
            }
            return new LlmResponse.TextResponse("deployment resumed");
        };

        AtomicReference<AgentState> state = new AtomicReference<>(AgentState.RUNNING);
        DefaultOrchestrator.AgentStateAccessor accessor = new DefaultOrchestrator.AgentStateAccessor() {
            @Override
            public AgentState getState() {
                return state.get();
            }

            @Override
            public void transitionTo(AgentState target) {
                state.set(target);
            }
        };

        DefaultOrchestrator orchestrator = new DefaultOrchestrator(
                new OrchestratorConfig(5, 10, 2, List.of()),
                accessor
        );

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> future = executor.submit(() -> orchestrator.run(context, llm));
            waitUntil(() -> state.get() == AgentState.PAUSED, 1000);

            assertEquals(1, llmCalls.get(), "LLM must not be called again while paused");
            Question waitingQuestion = questionRuntime.pendingQuestion("test-session").orElseThrow();

            Thread.sleep(150L);
            assertEquals(1, llmCalls.get(), "LLM call count should stay stable during the wait");

            questionRuntime.submitAnswer(
                    "test-session",
                    waitingQuestion.questionId(),
                    new Answer(
                            "Wait for CAB approval first.",
                            "This deployment is high risk and needs a human gate.",
                            "operator:alice",
                            AnswerSource.HUMAN_INLINE,
                            1.0d,
                            QuestionDecision.ANSWER_ACCEPTED,
                            DeliveryMode.PAUSE_WAIT_HUMAN,
                            "Human approval required before deploy.",
                            System.currentTimeMillis()
                    ));

            future.get(2, TimeUnit.SECONDS);
            assertEquals(2, llmCalls.get());
            assertTrue(conv.history().stream().anyMatch(message ->
                    message instanceof SystemMessage sm && sm.content().contains("Accepted answer for question")));
            assertInstanceOf(AssistantMessage.class, conv.get(conv.size() - 1));
            assertEquals("deployment resumed", ((AssistantMessage) conv.get(conv.size() - 1)).content());
            assertTrue(events.stream().anyMatch(event -> event.type() == EventType.QUESTION_CREATED));
            assertTrue(events.stream().anyMatch(event -> event.type() == EventType.QUESTION_ANSWERED));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void questionTimeoutEndsRunAndRejectsLateAnswer() {
        Conversation conv = new Conversation();
        SimpleEventBus eventBus = new SimpleEventBus();
        List<Event> events = Collections.synchronizedList(new ArrayList<>());
        eventBus.subscribe(EventType.QUESTION_EXPIRED, events::add);

        QuestionRuntime questionRuntime = new QuestionRuntime(eventBus);
        SimpleAgentContext context = questionCtx(
                Map.of("workDir", ".", "questionTimeoutSeconds", "1"),
                conv,
                questionRuntime
        );

        AtomicInteger llmCalls = new AtomicInteger(0);
        LlmClient llm = msgs -> {
            llmCalls.incrementAndGet();
            return new LlmResponse.ToolCallResponse(List.of(new ToolCall(
                    DefaultOrchestrator.QUESTION_TOOL_NAME,
                    Map.of(
                            "question", "Need rollback approval?",
                            "impact", "Rolling back too early may hide the real issue.",
                            "recommendation", "Wait for operator input.",
                            "category", QuestionCategory.PERMISSION_CONFIRMATION.name(),
                            "deliveryMode", DeliveryMode.PUSH_MOBILE_WAIT_HUMAN.name(),
                            "questionId", "timeout-q1"
                    ),
                    "call-timeout"
            )));
        };

        AtomicReference<AgentState> state = new AtomicReference<>(AgentState.RUNNING);
        DefaultOrchestrator orchestrator = new DefaultOrchestrator(
                new OrchestratorConfig(2, 10, 1, List.of()),
                new DefaultOrchestrator.AgentStateAccessor() {
                    @Override
                    public AgentState getState() {
                        return state.get();
                    }

                    @Override
                    public void transitionTo(AgentState target) {
                        state.set(target);
                    }
                }
        );

        orchestrator.run(context, llm);

        assertEquals(1, llmCalls.get(), "Timeout should end the run before the next LLM turn");
        assertTrue(events.stream().anyMatch(event -> event.type() == EventType.QUESTION_EXPIRED));
        assertThrows(IllegalStateException.class, () -> questionRuntime.submitAnswer(
                "test-session",
                "timeout-q1",
                new Answer(
                        "Reply arrived too late.",
                        "The timeout already ended the wait.",
                        "mobile:late",
                        AnswerSource.HUMAN_MOBILE,
                        1.0d,
                        QuestionDecision.ANSWER_ACCEPTED,
                        DeliveryMode.PUSH_MOBILE_WAIT_HUMAN,
                        "Human reply arrived after timeout.",
                        System.currentTimeMillis()
                )));
    }

    @Test
    void stopWhilePausedClosesWaitingQuestion() throws Exception {
        Conversation conv = new Conversation();
        QuestionRuntime questionRuntime = new QuestionRuntime(new SimpleEventBus());
        SimpleAgentContext context = questionCtx(
                Map.of("workDir", ".", "questionTimeoutSeconds", "5"),
                conv,
                questionRuntime
        );

        LlmClient llm = msgs -> new LlmResponse.ToolCallResponse(List.of(new ToolCall(
                DefaultOrchestrator.QUESTION_TOOL_NAME,
                Map.of(
                        "question", "Delete the old backup?",
                        "impact", "This action is irreversible.",
                        "recommendation", "Require human approval.",
                        "category", QuestionCategory.IRREVERSIBLE_APPROVAL.name(),
                        "questionId", "close-q1",
                        "deliveryMode", DeliveryMode.PAUSE_WAIT_HUMAN.name()
                ),
                "call-close"
        )));

        AtomicReference<AgentState> state = new AtomicReference<>(AgentState.RUNNING);
        DefaultOrchestrator orchestrator = new DefaultOrchestrator(
                new OrchestratorConfig(2, 10, 5, List.of()),
                new DefaultOrchestrator.AgentStateAccessor() {
                    @Override
                    public AgentState getState() {
                        return state.get();
                    }

                    @Override
                    public void transitionTo(AgentState target) {
                        state.set(target);
                    }
                }
        );

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> future = executor.submit(() -> orchestrator.run(context, llm));
            waitUntil(() -> state.get() == AgentState.PAUSED, 1000);

            state.set(AgentState.STOPPED);
            future.get(2, TimeUnit.SECONDS);

            assertTrue(questionRuntime.pendingQuestion("test-session").isEmpty());
            assertThrows(IllegalStateException.class, () -> questionRuntime.submitAnswer(
                    "test-session",
                    "close-q1",
                    new Answer(
                            "Do not delete it yet.",
                            "The run is already stopped.",
                            "operator:bob",
                            AnswerSource.HUMAN_INLINE,
                            1.0d,
                            QuestionDecision.ANSWER_ACCEPTED,
                            DeliveryMode.PAUSE_WAIT_HUMAN,
                            "Run already stopped.",
                            System.currentTimeMillis()
                    )));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void questionToolWithoutPauseTransitionLeavesNoPendingQuestion() {
        Conversation conv = new Conversation();
        QuestionRuntime questionRuntime = new QuestionRuntime(new SimpleEventBus());
        SimpleAgentContext context = questionCtx(
                Map.of("workDir", ".", "questionTimeoutSeconds", "1"),
                conv,
                questionRuntime
        );

        LlmClient llm = msgs -> new LlmResponse.ToolCallResponse(List.of(new ToolCall(
                DefaultOrchestrator.QUESTION_TOOL_NAME,
                Map.of(
                        "question", "Need approval?",
                        "impact", "This cannot continue without approval.",
                        "recommendation", "Pause for a human answer.",
                        "category", QuestionCategory.PERMISSION_CONFIRMATION.name(),
                        "questionId", "transition-q1",
                        "deliveryMode", DeliveryMode.PAUSE_WAIT_HUMAN.name()
                ),
                "call-transition"
        )));

        DefaultOrchestrator orchestrator = new DefaultOrchestrator(
                new OrchestratorConfig(2, 10, 1, List.of()),
                () -> AgentState.RUNNING
        );

        orchestrator.run(context, llm);

        assertTrue(questionRuntime.pendingQuestion("test-session").isEmpty());
        assertInstanceOf(ToolMessage.class, conv.get(conv.size() - 1));
        assertTrue(((ToolMessage) conv.get(conv.size() - 1)).content().contains("state transitions"));
    }

    private static void waitUntil(Callable<Boolean> condition, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.call()) {
                return;
            }
            Thread.sleep(25L);
        }
        fail("condition was not met within timeout");
    }
}
