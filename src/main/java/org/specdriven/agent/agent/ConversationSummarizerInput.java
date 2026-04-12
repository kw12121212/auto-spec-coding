package org.specdriven.agent.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Explicit inputs used to summarize conversation history for an LLM request.
 */
public record ConversationSummarizerInput(
        List<Message> messages,
        int recentMessageLimit,
        int tokenBudget,
        int summaryTokenBudget,
        Map<String, ContextRetentionCandidate> retentionCandidatesByMessageKey) {

    public ConversationSummarizerInput {
        if (recentMessageLimit < 0) {
            throw new IllegalArgumentException("recentMessageLimit must not be negative");
        }
        if (tokenBudget <= 0) {
            throw new IllegalArgumentException("tokenBudget must be positive");
        }
        if (summaryTokenBudget <= 0) {
            throw new IllegalArgumentException("summaryTokenBudget must be positive");
        }
        messages = messages == null ? List.of() : List.copyOf(messages);
        retentionCandidatesByMessageKey = copyRetentionCandidates(retentionCandidatesByMessageKey);
    }

    public static ConversationSummarizerInput of(List<Message> messages, int recentMessageLimit,
            int tokenBudget, int summaryTokenBudget) {
        return new ConversationSummarizerInput(messages, recentMessageLimit, tokenBudget, summaryTokenBudget, null);
    }

    public ConversationSummarizerInput withMessages(List<Message> replacementMessages) {
        return new ConversationSummarizerInput(
                replacementMessages,
                recentMessageLimit,
                tokenBudget,
                summaryTokenBudget,
                retentionCandidatesByMessageKey);
    }

    ContextRetentionCandidate retentionCandidateFor(Message message) {
        String primaryKey = messageKey(message);
        ContextRetentionCandidate explicitCandidate = retentionCandidatesByMessageKey.get(primaryKey);
        if (explicitCandidate != null) {
            return explicitCandidate;
        }
        if (message instanceof ToolMessage toolMessage) {
            String toolCallId = toolMessage.toolCallId() == null ? "" : toolMessage.toolCallId();
            explicitCandidate = retentionCandidatesByMessageKey.get(toolCallId);
            if (explicitCandidate != null) {
                return explicitCandidate;
            }
            return new ContextRetentionCandidate(
                    toolMessage.content(),
                    "",
                    "",
                    toolCallId,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false);
        }
        return ContextRetentionCandidate.ordinary(message.content());
    }

    public static String messageKey(Message message) {
        if (message == null) {
            return "";
        }
        if (message instanceof ToolMessage toolMessage && toolMessage.toolCallId() != null
                && !toolMessage.toolCallId().isBlank()) {
            return toolMessage.toolCallId();
        }
        return message.role() + ":" + message.timestamp();
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
