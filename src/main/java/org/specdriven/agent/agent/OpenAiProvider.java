package org.specdriven.agent.agent;

/**
 * LlmProvider implementation for OpenAI-compatible Chat Completions API endpoints.
 */
public class OpenAiProvider implements LlmProvider {

    private final LlmConfig config;

    public OpenAiProvider(LlmConfig config) {
        if (config == null) throw new IllegalArgumentException("config must not be null");
        this.config = config;
    }

    @Override
    public LlmConfig config() {
        return config;
    }

    @Override
    public LlmClient createClient() {
        return new OpenAiClient(config);
    }

    @Override
    public LlmClient createClient(LlmConfigSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot must not be null");
        return new OpenAiClient(new LlmConfig(
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
