package org.specdriven.agent.agent;

import java.util.List;
import java.util.Map;

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
     * Releases all resources held by this provider.
     */
    @Override
    void close();
}
