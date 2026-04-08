package org.specdriven.agent.jsonrpc;

/**
 * Interface for sending and receiving framed JSON-RPC 2.0 messages
 * over a bidirectional byte stream.
 */
public interface JsonRpcTransport extends AutoCloseable {

    /**
     * Start reading framed messages from the input stream and dispatching
     * them to the given handler. Spawns a background reader thread.
     *
     * @throws IllegalStateException if already running
     */
    void start(JsonRpcMessageHandler handler);

    /**
     * Stop the background reader thread and wait for it to terminate.
     *
     * @throws IllegalStateException if not running
     */
    void stop();

    /**
     * Send a JSON-RPC response as a framed message on the output stream.
     */
    void send(JsonRpcResponse response);

    /**
     * Send a JSON-RPC notification as a framed message on the output stream.
     */
    void send(JsonRpcNotification notification);
}
