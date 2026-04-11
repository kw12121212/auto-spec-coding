package org.specdriven.agent.agent;

import java.util.List;

/**
 * Computes how relevant a prior tool result is to the current turn.
 */
public interface ContextRelevanceScorer {

    /**
     * Returns a deterministic numeric score for the prior tool result.
     */
    int score(CurrentTurn currentTurn, PriorToolResult priorToolResult);

    /**
     * Explicit, caller-provided view of the current turn.
     */
    record CurrentTurn(String text, List<String> requestedToolNames) {
        public CurrentTurn {
            text = text == null ? "" : text;
            requestedToolNames = requestedToolNames == null ? List.of() : List.copyOf(requestedToolNames);
        }
    }

    /**
     * Explicit, caller-provided view of a prior tool result.
     */
    record PriorToolResult(String toolName, String content) {
        public PriorToolResult {
            toolName = toolName == null ? "" : toolName;
            content = content == null ? "" : content;
        }
    }
}
