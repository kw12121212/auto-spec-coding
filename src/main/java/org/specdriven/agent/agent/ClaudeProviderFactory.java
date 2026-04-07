package org.specdriven.agent.agent;

/**
 * Factory for creating {@link ClaudeProvider} instances.
 * Registered under key {@code "claude"} in the provider registry.
 */
public class ClaudeProviderFactory implements LlmProviderFactory {

    @Override
    public LlmProvider create(LlmConfig config) {
        return new ClaudeProvider(config);
    }
}
