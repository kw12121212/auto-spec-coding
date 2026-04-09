package org.specdriven.agent.testsupport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MockLspServerMain {

    private MockLspServerMain() {
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
                JsonRpcStdio.writeMessage(System.out, response(id, Map.of("capabilities", Map.of())));
                continue;
            }

            if ("shutdown".equals(method)) {
                JsonRpcStdio.writeMessage(System.out, response(id, null));
                return;
            }

            if ("textDocument/hover".equals(method)) {
                if ("tool".equals(mode)) {
                    JsonRpcStdio.writeMessage(System.out, response(id, Map.of("contents", "test hover")));
                } else if (!"slow".equals(mode)) {
                    JsonRpcStdio.writeMessage(System.out, response(id, Map.of("contents", "hover result")));
                }
                continue;
            }

            if ("textDocument/definition".equals(method)) {
                JsonRpcStdio.writeMessage(System.out, response(id, Map.of(
                        "uri", "file:///test.java",
                        "range", Map.of(
                                "start", Map.of("line", 0, "character", 0),
                                "end", Map.of("line", 0, "character", 5)))));
                continue;
            }

            if ("textDocument/references".equals(method)) {
                JsonRpcStdio.writeMessage(System.out, response(id, List.of(Map.of(
                        "uri", "file:///test.java",
                        "range", Map.of(
                                "start", Map.of("line", 0, "character", 0),
                                "end", Map.of("line", 0, "character", 5))))));
                continue;
            }

            if ("textDocument/documentSymbol".equals(method)) {
                JsonRpcStdio.writeMessage(System.out, response(id, List.of(Map.of(
                        "name", "TestClass",
                        "kind", 5,
                        "range", Map.of(
                                "start", Map.of("line", 0, "character", 0),
                                "end", Map.of("line", 10, "character", 1))))));
                continue;
            }

            if ("textDocument/didOpen".equals(method)) {
                String uri = uriFrom(message);
                JsonRpcStdio.writeMessage(System.out, notification(
                        "textDocument/publishDiagnostics",
                        Map.of("uri", uri, "diagnostics", diagnostics())));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static String uriFrom(Map<String, Object> message) {
        Object paramsObj = message.get("params");
        if (!(paramsObj instanceof Map<?, ?> params)) {
            return "";
        }
        Object textDocumentObj = params.get("textDocument");
        if (!(textDocumentObj instanceof Map<?, ?> textDocument)) {
            return "";
        }
        Object uri = textDocument.get("uri");
        return uri instanceof String value ? value : "";
    }

    private static Map<String, Object> response(Object id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    private static Map<String, Object> notification(String method, Object params) {
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        notification.put("params", params);
        return notification;
    }

    private static List<Map<String, Object>> diagnostics() {
        return List.of(Map.of(
                "range", Map.of(
                        "start", Map.of("line", 0, "character", 0),
                        "end", Map.of("line", 0, "character", 5)),
                "severity", 1,
                "message", "test error"));
    }
}
