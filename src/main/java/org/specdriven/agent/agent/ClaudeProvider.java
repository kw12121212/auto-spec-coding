package org.specdriven.agent.agent;

/**
 * LlmProvider implementation for the Anthropic Claude Messages API.
 */
public class ClaudeProvider implements LlmProvider {

    private final LlmConfig config;

    public ClaudeProvider(LlmConfig config) {
        if (config == null) throw new IllegalArgumentException("config must not be null");
        this.config = config;
    }

    @Override
    public LlmConfig config() {
        return config;
    }

    @Override
    public LlmClient createClient() {
        return new ClaudeClient(config);
    }

    @Override
    public void close() {
        // stateless — nothing to release
    }
}
