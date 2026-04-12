package org.specdriven.agent.agent;

/**
 * Immutable runtime snapshot of the effective LLM provider selection and config.
 */
public record LlmConfigSnapshot(
        String providerName,
        String baseUrl,
        String model,
        int timeout,
        int maxRetries
) {

    public LlmConfigSnapshot {
        if (providerName == null || providerName.isBlank()) {
            throw new IllegalArgumentException("providerName must not be null or blank");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be null or blank");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be null or blank");
        }
        if (timeout <= 0) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must not be negative");
        }
    }

    public static LlmConfigSnapshot of(String providerName, LlmConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        return new LlmConfigSnapshot(
                providerName,
                config.baseUrl(),
                config.model(),
                config.timeout(),
                config.maxRetries());
    }
}
