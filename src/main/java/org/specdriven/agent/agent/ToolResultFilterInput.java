package org.specdriven.agent.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Explicit inputs used to filter prior tool result messages for an LLM request.
 */
public record ToolResultFilterInput(
        String currentTurnText,
        List<String> requestedToolNames,
        List<Message> messages,
        Map<String, ContextRetentionCandidate> retentionCandidatesByToolCallId) {

    public ToolResultFilterInput {
        currentTurnText = currentTurnText == null ? "" : currentTurnText;
        requestedToolNames = requestedToolNames == null ? List.of() : List.copyOf(requestedToolNames);
        messages = messages == null ? List.of() : List.copyOf(messages);
        retentionCandidatesByToolCallId = copyRetentionCandidates(retentionCandidatesByToolCallId);
    }

    public static ToolResultFilterInput of(String currentTurnText, List<String> requestedToolNames,
            List<Message> messages) {
        return new ToolResultFilterInput(currentTurnText, requestedToolNames, messages, null);
    }

    public ToolResultFilterInput withMessages(List<Message> replacementMessages) {
        return new ToolResultFilterInput(
                currentTurnText,
                requestedToolNames,
                replacementMessages,
                retentionCandidatesByToolCallId);
    }

    ContextRetentionCandidate retentionCandidateFor(ToolMessage message, boolean relevantToCurrentTurn) {
        String toolCallId = message.toolCallId() == null ? "" : message.toolCallId();
        ContextRetentionCandidate explicitCandidate = retentionCandidatesByToolCallId.get(toolCallId);
        if (explicitCandidate != null) {
            return explicitCandidate;
        }
        return new ContextRetentionCandidate(
                message.content(),
                "",
                "",
                toolCallId,
                false,
                false,
                false,
                false,
                false,
                relevantToCurrentTurn);
    }

    private static Map<String, ContextRetentionCandidate> copyRetentionCandidates(
            Map<String, ContextRetentionCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Map.of();
        }

        LinkedHashMap<String, ContextRetentionCandidate> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ContextRetentionCandidate> entry : candidates.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(copy);
    }
}
