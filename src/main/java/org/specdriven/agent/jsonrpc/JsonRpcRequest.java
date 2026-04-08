package org.specdriven.agent.jsonrpc;

import java.util.Map;

/**
 * Immutable JSON-RPC 2.0 request object.
 * {@code id} must be non-null (Long, String, BigDecimal, etc.).
 * {@code params} may be a Map (by-name) or null (defaults to empty map in codec).
 */
public record JsonRpcRequest(Object id, String method, Object params) {

    public JsonRpcRequest {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null for a request");
        }
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("method must not be null or blank");
        }
    }

    public String jsonrpc() {
        return "2.0";
    }
}
