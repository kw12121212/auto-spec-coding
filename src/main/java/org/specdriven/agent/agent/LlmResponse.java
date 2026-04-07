package org.specdriven.agent.agent;

import java.util.Collections;
import java.util.List;
import java.util.Map;

 /**
 * Sealed result from an LLM call. Either a text reply or a request to invoke tools.
 */
public sealed interface LlmResponse
 permits LlmResponse.TextResponse, LlmResponse.ToolCallResponse {

    /**
     * Pure text reply from the LLM — signals the orchestrator to stop the loop.
     */
    record TextResponse(
            String content,
            LlmUsage usage,
            String finishReason
 ) implements LlmResponse {
        public TextResponse {
            if (content == null) throw new IllegalArgumentException("content must not be null");        }
        /**
         * Backward-compatible constructor without usage/finishReason.
         */
        public TextResponse(String content) {
            this(content, null, "stop");
        }
    }

    /**
     * The LLM requests one or more tool invocations — the orchestrator executes them and continues.
     */
    record ToolCallResponse(
            List<ToolCall> toolCalls,
            LlmUsage usage,
            String finishReason
 ) implements LlmResponse {
        public ToolCallResponse {
        toolCalls = toolCalls != null ? List.copyOf(toolCalls) : List.of();        }
        /**
         * Backward-compatible constructor without usage/finishReason.         */
        public ToolCallResponse(List<ToolCall> toolCalls) {
            this(toolCalls, null, "tool_calls");
        }
    }
}
