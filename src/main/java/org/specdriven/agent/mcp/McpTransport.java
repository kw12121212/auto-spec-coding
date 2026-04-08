package org.specdriven.agent.mcp;

import org.specdriven.agent.json.JsonReader;
import org.specdriven.agent.json.JsonWriter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * JSON-RPC 2.0 transport over stdin/stdout with Content-Length framing.
 * Handles request/response correlation via CompletableFuture and
 * dispatches incoming notifications and requests to a configurable handler.
 */
public class McpTransport implements AutoCloseable {

    private final OutputStream output;
    private final InputStream input;
    private final Object writeLock = new Object();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Map<Integer, CompletableFuture<Map<String, Object>>> pending = new ConcurrentHashMap<>();
    private final Thread readerThread;
    private volatile boolean running = true;
    private final Consumer<Map<String, Object>> messageHandler;

    /**
     * @param input          stream to read JSON-RPC messages from
     * @param output         stream to write JSON-RPC messages to
     * @param messageHandler handler for all incoming messages that are not
     *                       responses to pending requests (i.e., notifications
     *                       and requests from the other side)
     */
    public McpTransport(InputStream input, OutputStream output,
                        Consumer<Map<String, Object>> messageHandler) {
        this.input = input;
        this.output = output;
        this.messageHandler = messageHandler;
        this.readerThread = Thread.startVirtualThread(this::readLoop);
    }

    // --- Client role: send requests ---

    /**
     * Send a JSON-RPC request and wait for the correlated response.
     */
    public Map<String, Object> sendRequest(String method, Map<String, Object> params, int timeoutSec)
            throws Exception {
        int id = nextId.getAndIncrement();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        if (params != null) {
            request.put("params", params);
        }

        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        pending.put(id, future);
        sendMessage(request);

        try {
            return future.get(timeoutSec, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pending.remove(id);
            throw new RuntimeException("MCP request timed out after " + timeoutSec + "s: " + method);
        }
    }

    // --- Both roles: send notifications ---

    /**
     * Send a JSON-RPC notification (no ID, no response expected).
     */
    public void sendNotification(String method, Map<String, Object> params) {
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        if (params != null) {
            notification.put("params", params);
        }
        sendMessage(notification);
    }

    // --- Server role: send responses ---

    /**
     * Send a JSON-RPC success response.
     */
    public void sendResponse(Object id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result != null ? result : Map.of());
        sendMessage(response);
    }

    /**
     * Send a JSON-RPC error response.
     */
    public void sendError(Object id, int code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", Map.of("code", code, "message", message));
        sendMessage(response);
    }

    // --- Lifecycle ---

    @Override
    public void close() {
        running = false;
        for (var entry : pending.entrySet()) {
            entry.getValue().completeExceptionally(new RuntimeException("Transport closed"));
        }
        pending.clear();
        try { input.close(); } catch (IOException ignored) {}
        try { output.close(); } catch (IOException ignored) {}
    }

    // --- Internal ---

    private void sendMessage(Map<String, Object> message) {
        synchronized (writeLock) {
            try {
                String json = JsonWriter.fromMap(message);
                byte[] body = json.getBytes(StandardCharsets.UTF_8);
                String header = "Content-Length: " + body.length + "\r\n\r\n";
                output.write(header.getBytes(StandardCharsets.UTF_8));
                output.write(body);
                output.flush();
            } catch (IOException e) {
                throw new RuntimeException("Failed to send MCP message", e);
            }
        }
    }

    private void readLoop() {
        try {
            while (running) {
                int contentLength = readContentLength();
                if (contentLength < 0) break;
                if (contentLength == 0) continue;

                byte[] body = readBytes(contentLength);
                if (body == null) break;

                String json = new String(body, StandardCharsets.UTF_8);
                Map<String, Object> message = JsonReader.parseObject(json);
                dispatch(message);
            }
        } catch (Exception e) {
            if (running) {
                for (var entry : pending.entrySet()) {
                    entry.getValue().completeExceptionally(e);
                }
                pending.clear();
            }
        }
    }

    private void dispatch(Map<String, Object> message) {
        Object idObj = message.get("id");
        // Response to a pending outgoing request?
        if (idObj != null && (message.containsKey("result") || message.containsKey("error"))) {
            int id = ((Number) idObj).intValue();
            CompletableFuture<Map<String, Object>> future = pending.remove(id);
            if (future != null) {
                future.complete(message);
                return;
            }
        }
        // Everything else (notifications, incoming requests) goes to the handler
        if (messageHandler != null) {
            messageHandler.accept(message);
        }
    }

    private int readContentLength() throws IOException {
        String line;
        while ((line = readHeaderLine()) != null) {
            if (line.isEmpty()) continue;
            if (line.startsWith("Content-Length:")) {
                String next;
                while ((next = readHeaderLine()) != null && !next.isEmpty()) {}
                return Integer.parseInt(line.substring(15).trim());
            }
        }
        return -1;
    }

    private String readHeaderLine() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int prev = -1;
        while (true) {
            int b = input.read();
            if (b < 0) return null;
            if (prev == '\r' && b == '\n') {
                byte[] bytes = baos.toByteArray();
                return new String(bytes, 0, bytes.length - 1, StandardCharsets.UTF_8);
            }
            baos.write(b);
            prev = b;
        }
    }

    private byte[] readBytes(int count) throws IOException {
        byte[] buf = new byte[count];
        int read = 0;
        while (read < count) {
            int n = input.read(buf, read, count - read);
            if (n < 0) return null;
            read += n;
        }
        return buf;
    }
}
