package org.specdriven.agent.mcp;

import org.specdriven.agent.config.Config;
import org.specdriven.agent.tool.Tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages named MCP client connections.
 * Supports config-based initialization and bulk tool discovery.
 */
public class McpClientRegistry implements AutoCloseable {

    private final ConcurrentHashMap<String, McpClient> clients = new ConcurrentHashMap<>();
    private final int defaultTimeoutSeconds;

    public McpClientRegistry() {
        this(30);
    }

    public McpClientRegistry(int defaultTimeoutSeconds) {
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
    }

    /**
     * Create and initialize an MCP client for the given server.
     */
    public McpClient register(String name, String command) throws Exception {
        return register(name, command, defaultTimeoutSeconds);
    }

    /**
     * Create and initialize an MCP client for the given server with a specific timeout.
     */
    public McpClient register(String name, String command, int timeoutSeconds) throws Exception {
        McpClient client = new McpClient(command, timeoutSeconds);
        McpClient existing = clients.putIfAbsent(name, client);
        if (existing != null) {
            client.close();
            throw new IllegalArgumentException("MCP client '" + name + "' is already registered");
        }
        client.initialize();
        return client;
    }

    /**
     * Discover tools from a specific MCP client.
     */
    public List<Tool> discoverTools(String name) throws Exception {
        McpClient client = clients.get(name);
        if (client == null) {
            throw new IllegalArgumentException("No MCP client registered under name '" + name + "'");
        }

        List<Map<String, Object>> descriptors = client.toolsList();
        List<Tool> tools = new ArrayList<>();
        for (Map<String, Object> descriptor : descriptors) {
            tools.add(new McpToolAdapter(name, descriptor, client));
        }
        return tools;
    }

    /**
     * Discover tools from all registered MCP clients.
     */
    public List<Tool> discoverAllTools() throws Exception {
        List<Tool> allTools = new ArrayList<>();
        for (String name : clients.keySet()) {
            allTools.addAll(discoverTools(name));
        }
        return allTools;
    }

    /**
     * Create a registry from config entries under {@code mcp.servers}.
     *
     * <p>Expected config structure:</p>
     * <pre>
     * mcp:
     *   servers:
     *     my-server:
     *       command: "path/to/server --arg"
     *       timeout: 30
     * </pre>
     */
    public static McpClientRegistry fromConfig(Config config) {
        McpClientRegistry registry = new McpClientRegistry();
        Config serversSection;
        try {
            serversSection = config.getSection("mcp.servers");
        } catch (Exception e) {
            return registry; // No MCP servers configured
        }

        Map<String, String> flat = serversSection.asMap();
        // Group by server name — keys are like "my-server.command", "my-server.timeout"
        Map<String, Map<String, String>> serverConfigs = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> entry : flat.entrySet()) {
            String key = entry.getKey();
            int dot = key.indexOf('.');
            String serverName = dot < 0 ? key : key.substring(0, dot);
            String propKey = dot < 0 ? "" : key.substring(dot + 1);
            serverConfigs.computeIfAbsent(serverName, k -> new java.util.LinkedHashMap<>())
                    .put(propKey, entry.getValue());
        }

        for (Map.Entry<String, Map<String, String>> entry : serverConfigs.entrySet()) {
            String serverName = entry.getKey();
            Map<String, String> serverConf = entry.getValue();
            String command = serverConf.get("command");
            if (command == null || command.isBlank()) continue;

            int timeout = parseTimeout(serverConf);
            try {
                registry.register(serverName, command, timeout);
            } catch (Exception e) {
                // Skip servers that fail to initialize
            }
        }

        return registry;
    }

    private static int parseTimeout(Map<String, String> serverConf) {
        String timeoutStr = serverConf.get("timeout");
        if (timeoutStr != null) {
            try {
                return Integer.parseInt(timeoutStr);
            } catch (NumberFormatException ignored) {}
        }
        return 30;
    }

    @Override
    public void close() {
        for (McpClient client : clients.values()) {
            try {
                client.close();
            } catch (Exception ignored) {}
        }
        clients.clear();
    }
}
