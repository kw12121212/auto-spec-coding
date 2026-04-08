package org.specdriven.agent.mcp;

import org.specdriven.agent.tool.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP server that exposes registered Tool instances as MCP tools via stdio transport.
 * Handles initialize, tools/list, tools/call, and shutdown messages.
 */
public class McpServer implements AutoCloseable {

    private static final String PROTOCOL_VERSION = "2024-11-05";

    private final Map<String, Tool> toolRegistry;
    private final McpTransport transport;
    private volatile boolean running = true;

    /**
     * @param toolRegistry the local tools to expose via MCP
     * @param input        input stream (typically System.in)
     * @param output       output stream (typically System.out)
     */
    public McpServer(Map<String, Tool> toolRegistry, InputStream input, OutputStream output) {
        this.toolRegistry = new ConcurrentHashMap<>(toolRegistry);
        this.transport = new McpTransport(input, output, this::handleMessage);
    }

    /**
     * Block until shutdown is requested and processing completes.
     */
    public void awaitShutdown() throws InterruptedException {
        while (running) {
            Thread.sleep(100);
        }
    }

    @Override
    public void close() {
        running = false;
        transport.close();
    }

    // --- Message dispatch ---

    private void handleMessage(Map<String, Object> message) {
        String method = (String) message.get("method");
        Object id = message.get("id");

        if (method == null) return;

        switch (method) {
            case "initialize" -> handleInitialize(id);
            case "tools/list" -> handleToolsList(id);
            case "tools/call" -> handleToolsCall(id, message);
            case "shutdown" -> handleShutdown(id);
            default -> {
                if (id != null) {
                    transport.sendError(id, -32601, "Method not found: " + method);
                }
            }
        }
    }

    private void handleInitialize(Object id) {
        if (id == null) return;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", PROTOCOL_VERSION);
        result.put("serverInfo", Map.of("name", "spec-driven-agent", "version", "0.1.0"));
        result.put("capabilities", Map.of("tools", Map.of()));
        transport.sendResponse(id, result);
    }

    private void handleToolsList(Object id) {
        if (id == null) return;
        List<Map<String, Object>> tools = new ArrayList<>();
        for (Tool tool : toolRegistry.values()) {
            tools.add(toolToDescriptor(tool));
        }
        transport.sendResponse(id, Map.of("tools", tools));
    }

    @SuppressWarnings("unchecked")
    private void handleToolsCall(Object id, Map<String, Object> message) {
        if (id == null) return;

        Map<String, Object> params = (Map<String, Object>) message.get("params");
        if (params == null) {
            transport.sendError(id, -32602, "Missing params");
            return;
        }

        String toolName = (String) params.get("name");
        if (toolName == null) {
            transport.sendError(id, -32602, "Missing tool name");
            return;
        }

        Tool tool = toolRegistry.get(toolName);
        if (tool == null) {
            transport.sendError(id, -32602, "Unknown tool: " + toolName);
            return;
        }

        Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault("arguments", Map.of());
        ToolInput input = new ToolInput(arguments != null ? arguments : Map.of());

        try {
            ToolResult result = tool.execute(input, null);
            if (result instanceof ToolResult.Success success) {
                transport.sendResponse(id, Map.of(
                        "content", List.of(Map.of("type", "text", "text", success.output())),
                        "isError", false
                ));
            } else if (result instanceof ToolResult.Error error) {
                transport.sendResponse(id, Map.of(
                        "content", List.of(Map.of("type", "text", "text", error.message())),
                        "isError", true
                ));
            }
        } catch (Exception e) {
            transport.sendResponse(id, Map.of(
                    "content", List.of(Map.of("type", "text", "text", e.getMessage())),
                    "isError", true
            ));
        }
    }

    private void handleShutdown(Object id) {
        if (id != null) {
            transport.sendResponse(id, Map.of());
        }
        running = false;
    }

    // --- Tool descriptor conversion ---

    private static Map<String, Object> toolToDescriptor(Tool tool) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("name", tool.getName());
        descriptor.put("description", tool.getDescription());

        // Build inputSchema from ToolParameter list
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (ToolParameter param : tool.getParameters()) {
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", toolTypeToSchemaType(param.type()));
            prop.put("description", param.description());
            properties.put(param.name(), prop);
            if (param.required()) {
                required.add(param.name());
            }
        }
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        descriptor.put("inputSchema", schema);

        return descriptor;
    }

    private static String toolTypeToSchemaType(String toolType) {
        if (toolType == null) return "string";
        return switch (toolType) {
            case "number" -> "number";
            case "boolean" -> "boolean";
            case "array" -> "array";
            case "object" -> "object";
            default -> "string";
        };
    }
}
