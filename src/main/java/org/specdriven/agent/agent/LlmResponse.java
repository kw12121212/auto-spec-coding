package org.specdriven.agent.agent;

import java.util.List;

/**
 * Sealed result from an LLM call. Either a text reply or a request to invoke tools.
 */
public sealed interface LlmResponse
        permits LlmResponse.TextResponse, LlmResponse.ToolCallResponse {

    /**
     * Pure text reply from the LLM — signals the orchestrator to stop the loop.
     */
    record TextResponse(String content) implements LlmResponse {}

    /**
     * The LLM requests one or more tool invocations — the orchestrator executes them and continues.
     */
    record ToolCallResponse(List<ToolCall> toolCalls) implements LlmResponse {
        public ToolCallResponse {
            toolCalls = toolCalls != null ? List.copyOf(toolCalls) : List.of();
        }
    }
}
