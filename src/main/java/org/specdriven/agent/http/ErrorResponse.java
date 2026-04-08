package org.specdriven.agent.http;

import java.util.Map;

/**
 * Structured API error response.
 */
public record ErrorResponse(
        int status,
        String error,
        String message,
        Map<String, Object> details) {
}
