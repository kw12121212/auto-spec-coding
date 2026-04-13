package org.specdriven.agent.llm;

import org.specdriven.agent.agent.LlmConfigSnapshot;

/**
 * One persisted version of the default runtime LLM config snapshot.
 */
public record RuntimeLlmConfigVersion(
        long version,
        long persistedAt,
        LlmConfigSnapshot snapshot,
        boolean active
) {
}
