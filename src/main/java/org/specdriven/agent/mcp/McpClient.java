package org.specdriven.agent.mcp;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * MCP client managing a single external MCP server subprocess.
 * Performs initialize handshake, discovers tools, and delegates tool calls.
 */
public class McpClient implements AutoCloseable {

    private static final String PROTOCOL_VERSION = "2024-11-05";
    private static final Set<String> SUPPORTED_VERSIONS = Set.of("2024-11-05");

    private final Process process;
    private final McpTransport transport;
    private final int timeoutSeconds;
    private Map<String, Object> serverCapabilities;
    private boolean initialized = false;

    public McpClient(String command, int timeoutSeconds) throws IOException {
        this.timeoutSeconds = timeoutSeconds;
        ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
        pb.redirectErrorStream(false);
        this.process = pb.start();
        this.transport = new McpTransport(
                process.getInputStream(),
                process.getOutputStream(),
                null // no notification handler needed for client role
        );
    }

    // --- Lifecycle ---

    /**
     * Perform the MCP initialize handshake with the server.
     *
     * @throws Exception if handshake fails or protocol version is unsupported
     */
    public void initialize() throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.put("clientInfo", Map.of("name", "spec-driven-agent", "version", "0.1.0"));
        params.put("capabilities", Map.of());

        Map<String, Object> response = transport.sendRequest("initialize", params, timeoutSeconds);
        checkError(response);

        Map<String, Object> result = getResult(response);
        String serverVersion = (String) result.get("protocolVersion");
        if (serverVersion != null && !SUPPORTED_VERSIONS.contains(serverVersion)) {
            throw new RuntimeException("Unsupported MCP protocol version: " + serverVersion);
        }

        this.serverCapabilities = (Map<String, Object>) result.getOrDefault("capabilities", Map.of());

        // Send initialized notification
        transport.sendNotification("notifications/initialized", Map.of());
        this.initialized = true;
    }

    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public void close() {
        if (initialized) {
            try {
                transport.sendRequest("shutdown", Map.of(), 5);
            } catch (Exception ignored) {}
        }
        transport.close();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    // --- Tool discovery ---

    /**
     * Discover available tools from the connected MCP server.
     *
     * @return list of tool descriptors, each containing name, description, inputSchema
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> toolsList() throws Exception {
        Map<String, Object> response = transport.sendRequest("tools/list", Map.of(), timeoutSeconds);
        checkError(response);

        Map<String, Object> result = getResult(response);
        Object tools = result.get("tools");
        if (tools instanceof List<?> list) {
            return (List<Map<String, Object>>) (List<?>) list;
        }
        return List.of();
    }

    // --- Tool invocation ---

    /**
     * Call a tool on the MCP server.
     *
     * @param name      the tool name
     * @param arguments the tool arguments
     * @return the result content from the server
     */
    @SuppressWarnings("unchecked")
    public McpToolResult callTool(String name, Map<String, Object> arguments) throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", name);
        params.put("arguments", arguments != null ? arguments : Map.of());

        Map<String, Object> response = transport.sendRequest("tools/call", params, timeoutSeconds);
        checkError(response);

        Map<String, Object> result = getResult(response);
        boolean isError = Boolean.TRUE.equals(result.get("isError"));
        List<Map<String, Object>> content;
        Object rawContent = result.get("content");
        if (rawContent instanceof List<?> list) {
            content = (List<Map<String, Object>>) (List<?>) list;
        } else {
            content = List.of();
        }

        return new McpToolResult(content, isError);
    }

    // --- Internal ---

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getResult(Map<String, Object> response) throws Exception {
        Object result = response.get("result");
        if (result instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    private static void checkError(Map<String, Object> response) throws Exception {
        Object error = response.get("error");
        if (error instanceof Map<?, ?> err) {
            throw new RuntimeException("MCP error " + err.get("code") + ": " + err.get("message"));
        }
    }

    /**
     * Result of an MCP tool call.
     */
    public record McpToolResult(List<Map<String, Object>> content, boolean isError) {
        /**
         * Extract text content from the result.
         */
        public String extractText() {
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> item : content) {
                if ("text".equals(item.get("type"))) {
                    if (!sb.isEmpty()) sb.append("\n");
                    sb.append(item.get("text"));
                }
            }
            return sb.toString();
        }
    }
}
