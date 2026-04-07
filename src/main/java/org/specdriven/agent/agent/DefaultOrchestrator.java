package org.specdriven.agent.agent;

import org.specdriven.agent.tool.Tool;
import org.specdriven.agent.tool.ToolContext;
import org.specdriven.agent.tool.ToolInput;
import org.specdriven.agent.tool.ToolResult;
import org.specdriven.agent.permission.PermissionProvider;

import java.util.Collections;
import java.util.Map;

/**
 * Default implementation of the orchestrator loop:
 * receive → think → act → observe.
 *
 * <p>ToolCalls are executed sequentially in list order so that earlier
 * results are visible to later calls via the Conversation.
 * Tool execution errors are fed back to the LLM as ToolMessages
 * rather than terminating the loop.
 */
public class DefaultOrchestrator implements Orchestrator {

    private final OrchestratorConfig config;
    private final AgentStateAccessor stateAccessor;

    /**
     * Functional interface to check the agent's current state without
     * coupling the orchestrator to a concrete Agent implementation.
     */
    @FunctionalInterface
    public interface AgentStateAccessor {
        AgentState getState();
    }

    public DefaultOrchestrator(OrchestratorConfig config, AgentStateAccessor stateAccessor) {
        this.config = config;
        this.stateAccessor = stateAccessor;
    }

    @Override
    public void run(AgentContext context, LlmClient llmClient) {
        if (llmClient == null) {
            return;
        }
        Conversation conversation = context.conversation();
        if (conversation == null) {
            return;
        }

        int turn = 0;
        while (turn < config.maxTurns() && stateAccessor.getState() == AgentState.RUNNING) {
            turn++;

            // think — call LLM with full conversation history
            LlmResponse response = llmClient.chat(conversation.history());

            // record the assistant reply
            if (response instanceof LlmResponse.TextResponse text) {
                conversation.append(new AssistantMessage(text.content(), System.currentTimeMillis()));
                return; // no more tool calls — loop ends
            }

            if (response instanceof LlmResponse.ToolCallResponse toolCalls) {
                // record the assistant's tool-call intent as an assistant message
                conversation.append(new AssistantMessage(
                        toolCalls.toString(), System.currentTimeMillis()));

                // act — execute each tool call sequentially
                for (ToolCall call : toolCalls.toolCalls()) {
                    if (stateAccessor.getState() != AgentState.RUNNING) {
                        return;
                    }
                    executeToolCall(call, context, conversation);
                }
                // observe — loop continues, LLM sees all tool results
            }
        }
    }

    private void executeToolCall(ToolCall call, AgentContext context, Conversation conversation) {
        Map<String, Tool> registry = context.toolRegistry();
        Tool tool = registry != null ? registry.get(call.toolName()) : null;

        String resultContent;
        if (tool == null) {
            resultContent = "Error: tool not found: " + call.toolName();
        } else {
            try {
                ToolContext toolCtx = new SimpleToolContext(
                        context.config().getOrDefault("workDir", "."),
                        null,
                        Collections.emptyMap());
                ToolInput input = new ToolInput(call.parameters());
                ToolResult result = tool.execute(input, toolCtx);

                if (result instanceof ToolResult.Success success) {
                    resultContent = success.output();
                } else if (result instanceof ToolResult.Error err) {
                    resultContent = "Error: " + err.message();
                } else {
                    resultContent = "Error: unknown ToolResult type";
                }
            } catch (Exception e) {
                resultContent = "Error: " + e.getMessage();
            }
        }

        conversation.append(new ToolMessage(resultContent, System.currentTimeMillis(), call.toolName()));
    }

    /**
     * Minimal ToolContext implementation for orchestrator use.
     */
    private record SimpleToolContext(
            String workDir,
            PermissionProvider permissionProvider,
            Map<String, String> env
    ) implements ToolContext {}
}
