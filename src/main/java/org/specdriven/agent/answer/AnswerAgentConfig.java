package org.specdriven.agent.answer;

/**
 * Configuration for the Answer Agent.
 *
 * @param providerName       the LLM provider name (e.g., "openai", "claude")
 * @param model              the model name (e.g., "gpt-4o-mini")
 * @param temperature        the sampling temperature (0.0 - 2.0, default 0.3)
 * @param maxTokens          the maximum tokens in the response (default 1024)
 * @param timeoutSeconds     the timeout for LLM calls (default 30)
 * @param maxContextMessages the maximum number of context messages to include (default 10)
 */
public record AnswerAgentConfig(
        String providerName,
        String model,
        double temperature,
        int maxTokens,
        long timeoutSeconds,
        int maxContextMessages
) {
    private static final double DEFAULT_TEMPERATURE = 0.3;
    private static final int DEFAULT_MAX_TOKENS = 1024;
    private static final long DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_MAX_CONTEXT_MESSAGES = 10;

    /**
     * Creates a config with the given provider and model, using defaults for other fields.
     */
    public AnswerAgentConfig(String providerName, String model) {
        this(providerName, model, DEFAULT_TEMPERATURE, DEFAULT_MAX_TOKENS,
                DEFAULT_TIMEOUT_SECONDS, DEFAULT_MAX_CONTEXT_MESSAGES);
    }

    public AnswerAgentConfig {
        if (providerName == null || providerName.isBlank()) {
            throw new IllegalArgumentException("providerName must not be blank");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        if (temperature < 0.0 || temperature > 2.0) {
            throw new IllegalArgumentException("temperature must be between 0.0 and 2.0");
        }
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be positive");
        }
        if (maxContextMessages <= 0) {
            throw new IllegalArgumentException("maxContextMessages must be positive");
        }
    }

    /**
     * Returns the default configuration for OpenAI gpt-4o-mini.
     */
    public static AnswerAgentConfig openAiMiniDefaults() {
        return new AnswerAgentConfig("openai", "gpt-4o-mini");
    }
}
