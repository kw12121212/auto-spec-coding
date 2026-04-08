package org.specdriven.agent.jsonrpc;

import java.util.Map;

/**
 * Immutable JSON-RPC 2.0 notification — a request without an id.
 * No response is expected for notifications.
 */
public record JsonRpcNotification(String method, Object params) {

    public JsonRpcNotification {
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("method must not be null or blank");
        }
    }

    public String jsonrpc() {
        return "2.0";
    }
}
