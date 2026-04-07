package org.specdriven.agent.agent;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable request object encapsulating all parameters for an LLM call.
 *
 * @param messages     the conversation history (non-null, non-empty)
 * @param systemPrompt optional system prompt prepended to messages
 * @param tools        tool schemas available for the LLM to call
 * @param temperature  sampling temperature (0.0 - 2.0)
 * @param maxTokens    maximum tokens in the response
 */
public record LlmRequest(
        List<Message> messages,
        String systemPrompt,
        List<ToolSchema> tools,
        double temperature,
        int maxTokens,
        Map<String, String> extra
) {
    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final int DEFAULT_MAX_TOKENS = 4096;

    public LlmRequest {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be null or empty");
        }
        messages = List.copyOf(messages);
        tools = tools != null ? List.copyOf(tools) : List.of();
        extra = extra != null ? Map.copyOf(extra) : Map.of();
        if (temperature < 0.0 || temperature > 2.0) {
            throw new IllegalArgumentException("temperature must be between 0.0 and 2.0");
        }
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
    }

    /**
     * Creates a minimal request with just messages and default settings.
     *
     * @param messages the conversation history
     * @return a LlmRequest with default temperature, no tools, no system prompt
     */
    public static LlmRequest of(List<Message> messages) {
        return new LlmRequest(messages, null, null, DEFAULT_TEMPERATURE, DEFAULT_MAX_TOKENS, null);
    }

    /**
     * Creates a request with messages and a system prompt.
     *
     * @param messages     the conversation history
     * @param systemPrompt the system prompt
     * @return a LlmRequest with the given system prompt
     */
    public static LlmRequest of(List<Message> messages, String systemPrompt) {
        return new LlmRequest(messages, systemPrompt, null, DEFAULT_TEMPERATURE, DEFAULT_MAX_TOKENS, null);
    }
}
