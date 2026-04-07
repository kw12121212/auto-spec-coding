package org.specdriven.agent.agent;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.tool.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

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
                            List.of(new ToolCall("bash", Map.of("command", "ls"))));
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
                            new ToolCall("a", Map.of()),
                            new ToolCall("b", Map.of())));
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
                            List.of(new ToolCall("bash", Map.of("command", "rm -rf /"))));
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
                List.of(new ToolCall("bash", Map.of("command", "echo"))));
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
                List.of(new ToolCall("bash", Map.of())));
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
                            List.of(new ToolCall("nonexistent", Map.of())));
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
}
