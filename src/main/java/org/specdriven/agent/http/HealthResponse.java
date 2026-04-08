package org.specdriven.agent.http;

/**
 * Health check response.
 */
public record HealthResponse(
        String status,
        String version) {
}
