package org.specdriven.agent.jsonrpc;

/**
 * Callback interface for receiving decoded inbound messages from a {@link JsonRpcTransport}.
 */
public interface JsonRpcMessageHandler {

    /**
     * Called when a framed JSON-RPC request is received and decoded.
     */
    void onRequest(JsonRpcRequest request);

    /**
     * Called when a framed JSON-RPC notification is received and decoded.
     */
    void onNotification(JsonRpcNotification notification);

    /**
     * Called when a transport-level or decode error occurs.
     * The reader thread continues processing subsequent frames after recoverable errors.
     */
    void onError(Throwable error);
}
