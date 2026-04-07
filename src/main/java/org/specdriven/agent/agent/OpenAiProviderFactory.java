package org.specdriven.agent.agent;

/**
 * Factory for creating {@link OpenAiProvider} instances.
 * Registered under key {@code "openai"} in the provider registry.
 */
public class OpenAiProviderFactory implements LlmProviderFactory {

    @Override
    public LlmProvider create(LlmConfig config) {
        return new OpenAiProvider(config);
    }
}
