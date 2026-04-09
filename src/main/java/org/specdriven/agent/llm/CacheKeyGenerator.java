package org.specdriven.agent.llm;

import org.specdriven.agent.agent.LlmRequest;
import org.specdriven.agent.agent.Message;
import org.specdriven.agent.agent.ToolSchema;
import org.specdriven.agent.json.JsonWriter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Generates a deterministic SHA-256 cache key from an {@link LlmRequest}.
 * The key is derived from the request's systemPrompt, messages, tools, temperature, and maxTokens.
 */
public final class CacheKeyGenerator {

    private CacheKeyGenerator() {
    }

    /**
     * Generates a 64-character lowercase hex SHA-256 hash from the given request.
     *
     * @param request the LLM request
     * @return a 64-character lowercase hex string
     */
    public static String generate(LlmRequest request) {
        StringBuilder input = new StringBuilder();

        // System prompt
        input.append("sp:").append(request.systemPrompt() != null ? request.systemPrompt() : "").append('|');

        // Messages: role + content
        input.append("msg:");
        for (Message msg : request.messages()) {
            input.append(msg.role()).append(':').append(msg.content()).append(';');
        }
        input.append('|');

        // Tools: name + description + parameters JSON
        input.append("tools:");
        List<ToolSchema> tools = request.tools();
        if (tools != null) {
            for (ToolSchema tool : tools) {
                input.append(tool.name()).append(':')
                     .append(tool.description() != null ? tool.description() : "").append(':')
                     .append(JsonWriter.fromMap(tool.parameters())).append(';');
            }
        }
        input.append('|');

        // Temperature
        input.append("temp:").append(request.temperature()).append('|');

        // MaxTokens
        input.append("max:").append(request.maxTokens());

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
