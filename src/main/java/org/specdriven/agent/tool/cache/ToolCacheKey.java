package org.specdriven.agent.tool.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * Generates a deterministic SHA-256 cache key from a tool name and parameter map.
 */
public final class ToolCacheKey {

    private ToolCacheKey() {
    }

    /**
     * Generates a 64-character lowercase hex SHA-256 hash from the given tool name and parameters.
     * Parameters are sorted by key to ensure order independence.
     *
     * @param toolName the tool name
     * @param params   the tool parameters
     * @return a 64-character lowercase hex string
     */
    public static String generate(String toolName, Map<String, Object> params) {
        StringBuilder input = new StringBuilder();
        input.append("tool:").append(toolName).append('|');

        // Sort parameters by key for deterministic ordering
        input.append("params:");
        new TreeMap<>(params).forEach((k, v) ->
                input.append(k).append('=').append(v != null ? v.toString() : "null").append(';')
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
