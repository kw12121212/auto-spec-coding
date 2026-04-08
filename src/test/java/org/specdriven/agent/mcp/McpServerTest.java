package org.specdriven.agent.mcp;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.specdriven.agent.json.JsonReader;
import org.specdriven.agent.json.JsonWriter;
import org.specdriven.agent.tool.*;

class McpServerTest {

    // --- Initialize response ---

    @Test
    void initialize_respondsWithCapabilities() throws Exception {
        Map<String, Tool> tools = Map.of();
        PipedInputStream in = new PipedInputStream();
        PipedOutputStream out = new PipedOutputStream();
        PipedInputStream clientIn = new PipedInputStream();
        PipedOutputStream clientOut = new PipedOutputStream();

        out.connect(clientIn);
        clientOut.connect(in);

        try (McpServer server = new McpServer(tools, in, out)) {
            try (McpTransport client = new McpTransport(clientIn, clientOut, null)) {
                Map<String, Object> response = client.sendRequest("initialize", Map.of(
                        "protocolVersion", "2024-11-05",
                        "clientInfo", Map.of("name", "test-client")
                ), 5);

                Map<String, Object> result = (Map<String, Object>) response.get("result");
                assertEquals("2024-11-05", result.get("protocolVersion"));
                Map<String, Object> caps = (Map<String, Object>) result.get("capabilities");
                assertTrue(caps.containsKey("tools"));
            }
        }
    }

    // --- Tools/list response ---

    @Test
    void toolsList_returnsToolDescriptors() throws Exception {
        Tool mockTool = new Tool() {
            @Override public String getName() { return "greet"; }
            @Override public String getDescription() { return "Say hello"; }
            @Override public List<ToolParameter> getParameters() {
                return List.of(new ToolParameter("name", "string", "Person name", true));
            }
            @Override public ToolResult execute(ToolInput input, ToolContext context) {
                return new ToolResult.Success("Hello!");
            }
        };

        PipedInputStream in = new PipedInputStream();
        PipedOutputStream out = new PipedOutputStream();
        PipedInputStream clientIn = new PipedInputStream();
        PipedOutputStream clientOut = new PipedOutputStream();
        out.connect(clientIn);
        clientOut.connect(in);

        try (McpServer server = new McpServer(Map.of("greet", mockTool), in, out)) {
            try (McpTransport client = new McpTransport(clientIn, clientOut, null)) {
                // Initialize first
                client.sendRequest("initialize", Map.of(), 5);

                Map<String, Object> response = client.sendRequest("tools/list", Map.of(), 5);
                Map<String, Object> result = (Map<String, Object>) response.get("result");
                List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");

                assertEquals(1, tools.size());
                assertEquals("greet", tools.get(0).get("name"));
                assertEquals("Say hello", tools.get(0).get("description"));
                assertNotNull(tools.get(0).get("inputSchema"));
            }
        }
    }

    // --- Tools/call delegation ---

    @Test
    void toolsCall_delegatesToRegisteredTool() throws Exception {
        Tool mockTool = new Tool() {
            @Override public String getName() { return "echo"; }
            @Override public String getDescription() { return "Echo input"; }
            @Override public List<ToolParameter> getParameters() { return List.of(); }
            @Override public ToolResult execute(ToolInput input, ToolContext context) {
                String msg = (String) input.parameters().get("message");
                return new ToolResult.Success(msg != null ? msg : "empty");
            }
        };

        PipedInputStream in = new PipedInputStream();
        PipedOutputStream out = new PipedOutputStream();
        PipedInputStream clientIn = new PipedInputStream();
        PipedOutputStream clientOut = new PipedOutputStream();
        out.connect(clientIn);
        clientOut.connect(in);

        try (McpServer server = new McpServer(Map.of("echo", mockTool), in, out)) {
            try (McpTransport client = new McpTransport(clientIn, clientOut, null)) {
                client.sendRequest("initialize", Map.of(), 5);

                Map<String, Object> response = client.sendRequest("tools/call",
                        Map.of("name", "echo", "arguments", Map.of("message", "hello")), 5);

                Map<String, Object> result = (Map<String, Object>) response.get("result");
                assertFalse(Boolean.TRUE.equals(result.get("isError")));
                List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
                assertEquals("hello", content.get(0).get("text"));
            }
        }
    }

    @Test
    void toolsCall_errorTool_returnsIsError() throws Exception {
        Tool errorTool = new Tool() {
            @Override public String getName() { return "fail"; }
            @Override public String getDescription() { return "Always fails"; }
            @Override public List<ToolParameter> getParameters() { return List.of(); }
            @Override public ToolResult execute(ToolInput input, ToolContext context) {
                return new ToolResult.Error("intentional failure");
            }
        };

        PipedInputStream in = new PipedInputStream();
        PipedOutputStream out = new PipedOutputStream();
        PipedInputStream clientIn = new PipedInputStream();
        PipedOutputStream clientOut = new PipedOutputStream();
        out.connect(clientIn);
        clientOut.connect(in);

        try (McpServer server = new McpServer(Map.of("fail", errorTool), in, out)) {
            try (McpTransport client = new McpTransport(clientIn, clientOut, null)) {
                client.sendRequest("initialize", Map.of(), 5);

                Map<String, Object> response = client.sendRequest("tools/call",
                        Map.of("name", "fail", "arguments", Map.of()), 5);

                Map<String, Object> result = (Map<String, Object>) response.get("result");
                assertTrue(Boolean.TRUE.equals(result.get("isError")));
            }
        }
    }

    // --- Shutdown ---

    @Test
    void shutdown_respondsAndStops() throws Exception {
        PipedInputStream in = new PipedInputStream();
        PipedOutputStream out = new PipedOutputStream();
        PipedInputStream clientIn = new PipedInputStream();
        PipedOutputStream clientOut = new PipedOutputStream();
        out.connect(clientIn);
        clientOut.connect(in);

        McpServer server = new McpServer(Map.of(), in, out);
        try (McpTransport client = new McpTransport(clientIn, clientOut, null)) {
            Map<String, Object> response = client.sendRequest("shutdown", Map.of(), 5);
            assertTrue(response.containsKey("result"));
        }
        server.close();
    }

    // --- Unknown method ---

    @Test
    void unknownMethod_returnsError() throws Exception {
        PipedInputStream in = new PipedInputStream();
        PipedOutputStream out = new PipedOutputStream();
        PipedInputStream clientIn = new PipedInputStream();
        PipedOutputStream clientOut = new PipedOutputStream();
        out.connect(clientIn);
        clientOut.connect(in);

        try (McpServer server = new McpServer(Map.of(), in, out)) {
            try (McpTransport client = new McpTransport(clientIn, clientOut, null)) {
                Map<String, Object> response = client.sendRequest("nonexistent/method", Map.of(), 5);
                assertTrue(response.containsKey("error"));
                Map<?, ?> error = (Map<?, ?>) response.get("error");
                assertTrue(((String) error.get("message")).contains("Method not found"));
            }
        }
    }
}
