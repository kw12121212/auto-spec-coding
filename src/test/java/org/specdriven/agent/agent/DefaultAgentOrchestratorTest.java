package org.specdriven.agent.agent;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.tool.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DefaultAgentOrchestratorTest {

    @Test
    void doExecuteDelegatesToOrchestrator() {
        DefaultAgent agent = new DefaultAgent();
        agent.init(Map.of("orchestrator.maxTurns", "5"));
        agent.start();

        Conversation conv = new Conversation();
        Tool echoTool = new Tool() {
            @Override public String getName() { return "echo"; }
            @Override public String getDescription() { return ""; }
            @Override public List<ToolParameter> getParameters() { return List.of(); }
            @Override public ToolResult execute(ToolInput input, ToolContext ctx) {
                return new ToolResult.Success("echoed");
            }
        };

        AgentContext context = new AgentContext() {
            @Override public String sessionId() { return "test"; }
            @Override public Map<String, String> config() { return Map.of("workDir", "."); }
            @Override public Map<String, Tool> toolRegistry() { return Map.of("echo", echoTool); }
            @Override public Conversation conversation() { return conv; }
        };

        // Override createLlmClient to inject a mock
        DefaultAgent spyAgent = new DefaultAgent() {
            @Override
            protected LlmClient createLlmClient(AgentContext ctx) {
                return msgs -> new LlmResponse.TextResponse("final answer");
            }
        };
        spyAgent.init(Map.of());
        spyAgent.start();

        spyAgent.execute(context);

        assertEquals(1, conv.size());
        assertInstanceOf(AssistantMessage.class, conv.get(0));
        assertEquals("final answer", ((AssistantMessage) conv.get(0)).content());

        spyAgent.stop();
    }

    @Test
    void doExecuteWithToolCallLoop() {
        Tool bashTool = new Tool() {
            @Override public String getName() { return "bash"; }
            @Override public String getDescription() { return ""; }
            @Override public List<ToolParameter> getParameters() { return List.of(); }
            @Override public ToolResult execute(ToolInput input, ToolContext ctx) {
                return new ToolResult.Success("output");
            }
        };

        Conversation conv = new Conversation();
        AgentContext context = new AgentContext() {
            @Override public String sessionId() { return "test"; }
            @Override public Map<String, String> config() { return Map.of("workDir", "."); }
            @Override public Map<String, Tool> toolRegistry() { return Map.of("bash", bashTool); }
            @Override public Conversation conversation() { return conv; }
        };

        DefaultAgent agent = new DefaultAgent() {
            @Override
            protected LlmClient createLlmClient(AgentContext ctx) {
                return new LlmClient() {
                    private int callCount = 0;
                    @Override public LlmResponse chat(List<Message> msgs) {
                        callCount++;
                        if (callCount == 1) {
                            return new LlmResponse.ToolCallResponse(
                                    List.of(new ToolCall("bash", Map.of("command", "ls"))));
                        }
                        return new LlmResponse.TextResponse("done");
                    }
                };
            }
        };
        agent.init(Map.of());
        agent.start();
        agent.execute(context);

        // assistant (intent) + tool + assistant (final)
        assertEquals(3, conv.size());
        agent.stop();
    }
}
