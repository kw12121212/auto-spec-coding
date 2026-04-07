package org.specdriven.agent.agent;

/**
 * Factory interface for creating {@link LlmProvider} instances from {@link LlmConfig}.
 * Each concrete provider (OpenAI, Claude, etc.) provides its own factory implementation.
 */
@FunctionalInterface
public interface LlmProviderFactory {

    /**
     * Creates a new LlmProvider from the given configuration.
     *
     * @param config the provider configuration
     * @return a new LlmProvider instance
     */
    LlmProvider create(LlmConfig config);
}
