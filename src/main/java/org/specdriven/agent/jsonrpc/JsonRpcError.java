package org.specdriven.agent.jsonrpc;

import java.util.Map;

/**
 * Immutable JSON-RPC 2.0 error object with standard error code constants
 * and support for custom application error codes.
 */
public record JsonRpcError(int code, String message, Map<String, Object> data) {

    /** Standard error: Parse error (-32700). */
    public static JsonRpcError parseError() {
        return new JsonRpcError(-32700, "Parse error", null);
    }

    /** Standard error: Invalid Request (-32600). */
    public static JsonRpcError invalidRequest() {
        return new JsonRpcError(-32600, "Invalid Request", null);
    }

    /** Standard error: Method not found (-32601). */
    public static JsonRpcError methodNotFound() {
        return new JsonRpcError(-32601, "Method not found", null);
    }

    /** Standard error: Invalid params (-32602). */
    public static JsonRpcError invalidParams() {
        return new JsonRpcError(-32602, "Invalid params", null);
    }

    /** Standard error: Internal error (-32603). */
    public static JsonRpcError internalError() {
        return new JsonRpcError(-32603, "Internal error", null);
    }

    public JsonRpcError {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be null or blank");
        }
    }
}
