package org.specdriven.agent.jsonrpc;

/**
 * Immutable JSON-RPC 2.0 response object.
 * A response is either a success (with a result) or an error (with an error).
 * Use {@link #success} or {@link #error} factory methods to construct.
 */
public final class JsonRpcResponse {

    private final Object id;
    private final Object result;
    private final JsonRpcError error;
    private final boolean success;

    /** Create a success response. Result may be null. */
    public static JsonRpcResponse success(Object id, Object result) {
        return new JsonRpcResponse(id, result, null, true);
    }

    /** Create an error response. */
    public static JsonRpcResponse error(Object id, JsonRpcError error) {
        if (error == null) {
            throw new IllegalArgumentException("error must not be null");
        }
        return new JsonRpcResponse(id, null, error, false);
    }

    /**
     * General-purpose constructor. Rejects both-null and both-non-null
     * combinations of result and error.
     */
    public JsonRpcResponse(Object id, Object result, JsonRpcError error) {
        if (result != null && error != null) {
            throw new IllegalArgumentException("Cannot have both result and error");
        }
        if (result == null && error == null) {
            throw new IllegalArgumentException("Either result or error must be non-null");
        }
        this.id = id;
        this.result = result;
        this.error = error;
        this.success = error == null;
    }

    private JsonRpcResponse(Object id, Object result, JsonRpcError error, boolean success) {
        this.id = id;
        this.result = result;
        this.error = error;
        this.success = success;
    }

    public String jsonrpc() { return "2.0"; }

    public Object id() { return id; }

    /** The result value. Returns null for error responses. May be null for success responses. */
    public Object result() { return success ? result : null; }

    /** The error. Returns null for success responses. */
    public JsonRpcError error() { return success ? null : error; }

    public boolean isSuccess() { return success; }
}
