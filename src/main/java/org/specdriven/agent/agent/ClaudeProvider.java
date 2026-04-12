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
    public LlmClient createClient(LlmConfigSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot must not be null");
        return new ClaudeClient(new LlmConfig(
                snapshot.baseUrl(),
                config.apiKey(),
                snapshot.model(),
                snapshot.timeout(),
                snapshot.maxRetries()));
    }

    @Override
    public void close() {
        // stateless — nothing to release
    }
}
