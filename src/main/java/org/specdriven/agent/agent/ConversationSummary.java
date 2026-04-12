package org.specdriven.agent.agent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deterministic summary metadata for compressed conversation history.
 */
public record ConversationSummary(
        int compressedMessageCount,
        Map<String, Integer> roleCounts,
        Map<String, Integer> toolCounts,
        String content) {

    public ConversationSummary {
        if (compressedMessageCount < 0) {
            throw new IllegalArgumentException("compressedMessageCount must not be negative");
        }
        roleCounts = unmodifiableCopy(roleCounts);
        toolCounts = unmodifiableCopy(toolCounts);
        content = content == null ? "" : content;
    }

    public AssistantMessage toMessage(long timestamp) {
        return new AssistantMessage(content, timestamp);
    }

    private static Map<String, Integer> unmodifiableCopy(Map<String, Integer> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Integer> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(copy);
    }
}
