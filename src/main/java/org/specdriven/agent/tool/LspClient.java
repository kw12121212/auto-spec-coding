package org.specdriven.agent.tool;

import org.specdriven.agent.json.JsonReader;
import org.specdriven.agent.json.JsonWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LSP client managing a single language server process.
 * Handles JSON-RPC 2.0 transport over stdin/stdout with Content-Length framing.
 */
public class LspClient implements AutoCloseable {

    private final Process process;
    private final OutputStream outputStream;
    private final InputStream inputStream;
    private final Object writeLock = new Object();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Map<Integer, CompletableFuture<Map<String, Object>>> pending = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> diagnosticsByUri = new ConcurrentHashMap<>();
    private final Thread readerThread;
    private volatile boolean running = true;
    private volatile boolean initialized = false;
    private final int defaultTimeoutSeconds;

    public LspClient(List<String> serverCommand, int defaultTimeoutSeconds) throws IOException {
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
        ProcessBuilder pb = new ProcessBuilder(serverCommand);
        pb.redirectErrorStream(false);
        this.process = pb.start();
        this.outputStream = process.getOutputStream();
        this.inputStream = process.getInputStream();
        this.readerThread = Thread.startVirtualThread(this::readLoop);
    }

    // --- Lifecycle ---

    public void initialize(String rootUri) throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("processId", ProcessHandle.current().pid());
        params.put("rootUri", rootUri);
        params.put("capabilities", Map.of(
                "textDocument", Map.of("publishDiagnostics", Map.of())
        ));

        Map<String, Object> response = sendRequest("initialize", params);
        checkError(response);
        sendNotification("initialized", Map.of());
        initialized = true;
    }

    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public void close() {
        running = false;
        try {
            if (initialized) {
                try {
                    sendRequest("shutdown", Map.of(), 5);
                } catch (Exception ignored) {
                }
                sendNotification("exit", Map.of());
            }
        } catch (Exception ignored) {
        }
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
        try {
            outputStream.close();
        } catch (IOException ignored) {
        }
        try {
            inputStream.close();
        } catch (IOException ignored) {
        }
    }

    // --- TextDocument operations ---

    public void textDocumentDidOpen(String fileUri, String languageId, String text) {
        Map<String, Object> td = new LinkedHashMap<>();
        td.put("uri", fileUri);
        td.put("languageId", languageId);
        td.put("version", 1);
        td.put("text", text);
        sendNotification("textDocument/didOpen", Map.of("textDocument", td));
    }

    public void textDocumentDidClose(String fileUri) {
        sendNotification("textDocument/didClose",
                Map.of("textDocument", Map.of("uri", fileUri)));
    }

    public Map<String, Object> hover(String fileUri, int line, int character) throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("textDocument", Map.of("uri", fileUri));
        params.put("position", Map.of("line", line, "character", character));
        return sendRequest("textDocument/hover", params);
    }

    public Map<String, Object> definition(String fileUri, int line, int character) throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("textDocument", Map.of("uri", fileUri));
        params.put("position", Map.of("line", line, "character", character));
        return sendRequest("textDocument/definition", params);
    }

    public Map<String, Object> references(String fileUri, int line, int character) throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("textDocument", Map.of("uri", fileUri));
        params.put("position", Map.of("line", line, "character", character));
        params.put("context", Map.of("includeDeclaration", true));
        return sendRequest("textDocument/references", params);
    }

    public Map<String, Object> documentSymbol(String fileUri) throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("textDocument", Map.of("uri", fileUri));
        return sendRequest("textDocument/documentSymbol", params);
    }

    // --- Diagnostics ---

    public List<Map<String, Object>> waitForDiagnostics(String fileUri, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            List<Map<String, Object>> diags = diagnosticsByUri.get(fileUri);
            if (diags != null) return diags;
            Thread.sleep(50);
        }
        return diagnosticsByUri.getOrDefault(fileUri, List.of());
    }

    // --- JSON-RPC transport ---

    private Map<String, Object> sendRequest(String method, Map<String, Object> params) throws Exception {
        return sendRequest(method, params, defaultTimeoutSeconds);
    }

    private Map<String, Object> sendRequest(String method, Map<String, Object> params, int timeoutSec)
            throws Exception {
        int id = nextId.getAndIncrement();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.put("params", params);

        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        pending.put(id, future);

        sendMessage(request);

        try {
            return future.get(timeoutSec, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pending.remove(id);
            throw new RuntimeException("LSP request timed out after " + timeoutSec + "s: " + method);
        }
    }

    private void sendNotification(String method, Map<String, Object> params) {
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        notification.put("params", params);
        sendMessage(notification);
    }

    private void sendMessage(Map<String, Object> message) {
        synchronized (writeLock) {
            try {
                String json = JsonWriter.fromMap(message);
                byte[] body = json.getBytes(StandardCharsets.UTF_8);
                String header = "Content-Length: " + body.length + "\r\n\r\n";
                outputStream.write(header.getBytes(StandardCharsets.UTF_8));
                outputStream.write(body);
                outputStream.flush();
            } catch (IOException e) {
                throw new RuntimeException("Failed to send LSP message", e);
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
                handleMessage(message);
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

    private int readContentLength() throws IOException {
        String line;
        while ((line = readHeaderLine()) != null) {
            if (line.isEmpty()) continue;
            if (line.startsWith("Content-Length:")) {
                // Drain remaining headers until blank line
                String next;
                while ((next = readHeaderLine()) != null && !next.isEmpty()) {
                }
                return Integer.parseInt(line.substring(15).trim());
            }
        }
        return -1;
    }

    private String readHeaderLine() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int prev = -1;
        while (true) {
            int b = inputStream.read();
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
            int n = inputStream.read(buf, read, count - read);
            if (n < 0) return null;
            read += n;
        }
        return buf;
    }

    @SuppressWarnings("unchecked")
    private void handleMessage(Map<String, Object> message) {
        Object idObj = message.get("id");
        if (idObj != null) {
            // Response (success or error)
            int id = ((Number) idObj).intValue();
            CompletableFuture<Map<String, Object>> future = pending.remove(id);
            if (future != null) {
                future.complete(message);
            }
        } else if (message.containsKey("method")) {
            String method = (String) message.get("method");
            if ("textDocument/publishDiagnostics".equals(method)) {
                Map<String, Object> params = (Map<String, Object>) message.get("params");
                if (params != null) {
                    String uri = (String) params.get("uri");
                    List<Map<String, Object>> diags = (List<Map<String, Object>>) params.get("diagnostics");
                    if (uri != null) {
                        diagnosticsByUri.put(uri, diags != null ? diags : List.of());
                    }
                }
            }
        }
    }

    private static void checkError(Map<String, Object> response) throws Exception {
        Object error = response.get("error");
        if (error instanceof Map<?, ?> err) {
            throw new RuntimeException("LSP error " + err.get("code") + ": " + err.get("message"));
        }
    }
}
