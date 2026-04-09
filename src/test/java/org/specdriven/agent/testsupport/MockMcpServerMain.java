package org.specdriven.agent.testsupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MockMcpServerMain {

    private MockMcpServerMain() {
    }

    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "standard" : args[0];
        while (true) {
            Map<String, Object> message = JsonRpcStdio.readMessage(System.in);
            if (message == null) {
                return;
            }

            String method = (String) message.get("method");
            Object id = message.get("id");
            if ("initialize".equals(method)) {
                JsonRpcStdio.writeMessage(System.out, response(id, switch (mode) {
                    case "bad-version" -> Map.of(
                            "protocolVersion", "2099-01-01",
                            "capabilities", Map.of());
                    default -> Map.of(
                            "protocolVersion", "2024-11-05",
                            "capabilities", Map.of("tools", Map.of()),
                            "serverInfo", Map.of("name", "mock", "version", "0.1.0"));
                }));
                continue;
            }

            if ("notifications/initialized".equals(method)) {
                continue;
            }

            if ("tools/list".equals(method)) {
                JsonRpcStdio.writeMessage(System.out, response(id, Map.of("tools", toolList())));
                continue;
            }

            if ("tools/call".equals(method)) {
                if ("slow".equals(mode)) {
                    Thread.sleep(60_000);
                    continue;
                }
                if ("error".equals(mode)) {
                    JsonRpcStdio.writeMessage(System.out, response(id, Map.of(
                            "content", List.of(Map.of("type", "text", "text", "something went wrong")),
                            "isError", true)));
                    continue;
                }

                JsonRpcStdio.writeMessage(System.out, response(id, Map.of(
                        "content", List.of(Map.of("type", "text", "text", "file content here")),
                        "isError", false)));
                continue;
            }

            if ("shutdown".equals(method)) {
                JsonRpcStdio.writeMessage(System.out, response(id, Map.of()));
                return;
            }
        }
    }

    private static Map<String, Object> response(Object id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    private static List<Map<String, Object>> toolList() {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(tool(
                "read_file",
                "Read a file",
                Map.of("path", Map.of("type", "string", "description", "File path")),
                List.of("path")));
        tools.add(tool(
                "write_file",
                "Write a file",
                Map.of(
                        "path", Map.of("type", "string", "description", "File path"),
                        "content", Map.of("type", "string", "description", "File content")),
                List.of("path", "content")));
        return tools;
    }

    private static Map<String, Object> tool(String name, String description,
                                            Map<String, Object> properties,
                                            List<String> required) {
        return Map.of(
                "name", name,
                "description", description,
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", properties,
                        "required", required));
    }
}
