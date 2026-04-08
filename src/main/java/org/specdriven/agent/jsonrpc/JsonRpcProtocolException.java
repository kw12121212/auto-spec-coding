package org.specdriven.agent.jsonrpc;

/**
 * Protocol-level exception for JSON-RPC 2.0 decode errors.
 * Carries the corresponding JSON-RPC error code.
 */
public class JsonRpcProtocolException extends RuntimeException {

    private final int errorCode;

    public JsonRpcProtocolException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public JsonRpcProtocolException(int errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
