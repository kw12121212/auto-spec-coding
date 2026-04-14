package org.specdriven.agent.agent;

import java.util.Map;

/**
 * Immutable configuration for an LLM provider.
 * Supports construction from a Map&lt;String, String&gt; for compatibility
 * with {@code AgentContext.config()}.
 *
 * @param baseUrl    the provider API endpoint (non-null)
 * @param apiKey     the API key for authentication (non-null)
 * @param model      the model identifier (e.g. "gpt-4", "claude-3-opus")
 * @param timeout    request timeout in seconds
 * @param maxRetries maximum number of retry attempts on transient failures
 */
public record LlmConfig(
        String baseUrl,
        String apiKey,
        String model,
        int timeout,
        int maxRetries
) {
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String DEFAULT_MODEL = "gpt-4";
    private static final int DEFAULT_TIMEOUT = 60;
    private static final int DEFAULT_MAX_RETRIES = 3;

    public LlmConfig {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be null or blank");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey must not be null or blank");
        }
        if (timeout <= 0) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must not be negative");
        }
    }

    /**
     * Creates a config from a key-value map, falling back to sensible defaults
     * for missing keys. baseUrl and apiKey are required.
     *
     * @param map configuration key-value pairs
     * @return a LlmConfig with mapped values
     * @throws IllegalArgumentException if baseUrl or apiKey is missing
     */
    public static LlmConfig fromMap(Map<String, String> map) {
        if (map == null) {
            throw new IllegalArgumentException("config map must not be null");
        }
        String baseUrl = map.getOrDefault("baseUrl", DEFAULT_BASE_URL);
        String apiKey = map.get("apiKey");
        String model = map.getOrDefault("model", DEFAULT_MODEL);
        int timeout = parseInt(map, "timeout", DEFAULT_TIMEOUT);
        int maxRetries = parseInt(map, "maxRetries", DEFAULT_MAX_RETRIES);
        return new LlmConfig(baseUrl, apiKey, model, timeout, maxRetries);
    }

    @Override
    public String toString() {
        return "LlmConfig[baseUrl=" + baseUrl
                + ", apiKey=***"
                + ", model=" + model
                + ", timeout=" + timeout
                + ", maxRetries=" + maxRetries + "]";
    }

    private static int parseInt(Map<String, String> map, String key, int defaultValue) {
        String value = map.get(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
