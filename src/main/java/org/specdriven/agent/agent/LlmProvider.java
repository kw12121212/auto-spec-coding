package org.specdriven.agent.agent;

import java.util.Objects;

/**
 * Represents a configured LLM provider capable of creating {@link LlmClient} instances.
 */
public interface LlmProvider extends AutoCloseable {
    /**
     * Returns the configuration for this provider.
     */
    LlmConfig config();

    /**
     * Creates a new LlmClient instance ready for use.
     */
    LlmClient createClient();

    /**
     * Creates a client bound to a specific runtime config snapshot.
     * Default implementation falls back to {@link #createClient()} for providers
     * that do not yet vary client construction by snapshot.
     */
    default LlmClient createClient(LlmConfigSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        return createClient();
    }

    /**
     * Releases all resources held by this provider.
     */
    @Override
    void close();
}
