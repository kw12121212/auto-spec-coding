package org.specdriven.agent.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for smart context optimization before LLM calls.
 */
public record SmartContextInjectorConfig(
        boolean enabled,
        int recentMessageLimit,
        int tokenBudget,
        int summaryTokenBudget,
        boolean useExplicitCurrentTurn,
        String currentTurnText,
        List<String> requestedToolNames,
        Map<String, ContextRetentionCandidate> retentionCandidatesByToolCallId,
        Map<String, ContextRetentionCandidate> retentionCandidatesByMessageKey
) {
    private static final int DEFAULT_RECENT_MESSAGE_LIMIT = 8;
    private static final int DEFAULT_SUMMARY_TOKEN_BUDGET = 512;

    public SmartContextInjectorConfig {
        if (recentMessageLimit < 0) {
            throw new IllegalArgumentException("recentMessageLimit must not be negative");
        }
        if (tokenBudget <= 0) {
            throw new IllegalArgumentException("tokenBudget must be positive");
        }
        if (summaryTokenBudget <= 0) {
            throw new IllegalArgumentException("summaryTokenBudget must be positive");
        }
        currentTurnText = currentTurnText == null ? "" : currentTurnText;
        requestedToolNames = requestedToolNames == null ? List.of() : List.copyOf(requestedToolNames);
        retentionCandidatesByToolCallId = copyRetentionCandidates(retentionCandidatesByToolCallId);
        retentionCandidatesByMessageKey = copyRetentionCandidates(retentionCandidatesByMessageKey);
    }

    public static SmartContextInjectorConfig defaults(int tokenBudget) {
        int summaryBudget = Math.min(DEFAULT_SUMMARY_TOKEN_BUDGET, Math.max(1, tokenBudget / 5));
        return new SmartContextInjectorConfig(
                true,
                DEFAULT_RECENT_MESSAGE_LIMIT,
                tokenBudget,
                summaryBudget,
                false,
                "",
                List.of(),
                null,
                null);
    }

    public static SmartContextInjectorConfig disabled() {
        return new SmartContextInjectorConfig(false, 0, 1, 1, false, "", List.of(), null, null);
    }

    public SmartContextInjectorConfig withCurrentTurn(String turnText, List<String> toolNames) {
        return new SmartContextInjectorConfig(
                enabled,
                recentMessageLimit,
                tokenBudget,
                summaryTokenBudget,
                true,
                turnText,
                toolNames,
                retentionCandidatesByToolCallId,
                retentionCandidatesByMessageKey);
    }

    public SmartContextInjectorConfig withRetentionCandidates(
            Map<String, ContextRetentionCandidate> toolCallCandidates,
            Map<String, ContextRetentionCandidate> messageCandidates) {
        return new SmartContextInjectorConfig(
                enabled,
                recentMessageLimit,
                tokenBudget,
                summaryTokenBudget,
                useExplicitCurrentTurn,
                currentTurnText,
                requestedToolNames,
                toolCallCandidates,
                messageCandidates);
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
