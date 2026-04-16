package org.specdriven.agent.http;

import org.specdriven.agent.json.JsonReader;
import org.specdriven.agent.json.JsonWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JSON codec for HTTP API model types.
 * Uses the project's existing JsonWriter/JsonReader — no external dependencies.
 */
public final class HttpJsonCodec {

    private HttpJsonCodec() {}

    // --- Encode ---

    public static String encode(RunAgentResponse r) {
        return JsonWriter.object()
                .field("agentId", r.agentId())
                .field("output", r.output())
                .field("state", r.state())
                .build();
    }

    public static String encode(AgentStateResponse r) {
        return JsonWriter.object()
                .field("agentId", r.agentId())
                .field("state", r.state())
                .field("createdAt", r.createdAt())
                .field("updatedAt", r.updatedAt())
                .build();
    }

    public static String encode(ToolInfo t) {
        List<String> paramJson = new ArrayList<>();
        for (Map<String, Object> p : t.parameters()) {
            paramJson.add(JsonWriter.fromMap(p));
        }
        return JsonWriter.object()
                .field("name", t.name())
                .field("description", t.description())
                .arrayField("parameters", paramJson)
                .build();
    }

    public static String encode(ToolsListResponse r) {
        List<String> toolJson = new ArrayList<>();
        for (ToolInfo t : r.tools()) {
            toolJson.add(encode(t));
        }
        return JsonWriter.object()
                .arrayField("tools", toolJson)
                .build();
    }

    public static String encode(HealthResponse r) {
        return JsonWriter.object()
                .field("status", r.status())
                .field("version", r.version())
                .build();
    }

    public static String encode(ErrorResponse r) {
        JsonWriter w = JsonWriter.object()
                .field("status", r.status())
                .field("error", r.error())
                .field("message", r.message());
        if (r.details() != null) {
            w.rawField("details", JsonWriter.fromMap(r.details()));
        } else {
            w.field("details", (String) null);
        }
        return w.build();
    }

    public static String encode(PlatformHealthResponse r) {
        List<String> subsystemJson = new ArrayList<>();
        for (PlatformHealthResponse.SubsystemEntry entry : r.subsystems()) {
            subsystemJson.add(JsonWriter.object()
                    .field("name", entry.name())
                    .field("status", entry.status())
                    .field("message", entry.message())
                    .build());
        }
        return JsonWriter.object()
                .field("overallStatus", r.overallStatus())
                .arrayField("subsystems", subsystemJson)
                .field("probedAt", r.probedAt())
                .build();
    }

    public static String encode(WorkflowInstanceResponse r) {
        return JsonWriter.object()
                .field("workflowId", r.workflowId())
                .field("workflowName", r.workflowName())
                .field("status", r.status())
                .field("createdAt", r.createdAt())
                .field("updatedAt", r.updatedAt())
                .build();
    }

    public static String encode(WorkflowResultResponse r) {
        return JsonWriter.object()
                .field("workflowId", r.workflowId())
                .field("workflowName", r.workflowName())
                .field("status", r.status())
                .rawField("result", encodeJsonValue(r.result()))
                .field("failureSummary", r.failureSummary())
                .field("createdAt", r.createdAt())
                .field("updatedAt", r.updatedAt())
                .build();
    }

    public static String encode(ServiceInvocationResponse r) {
        return JsonWriter.object()
                .rawField("result", encodeJsonValue(r.result()))
                .build();
    }

    public static String encode(RemoteToolInvocationRequest r) {
        return JsonWriter.object()
                .field("toolName", r.toolName())
                .rawField("parameters", JsonWriter.fromMap(r.parameters()))
                .build();
    }

    // --- Decode ---

    public static RunAgentRequest decodeRequest(String json) {
        Map<String, Object> root = JsonReader.parseObject(json);
        Object promptRaw = root.get("prompt");
        if (promptRaw == null) {
            throw new HttpApiException(400, "invalid_params", "Missing required field: prompt");
        }
        String prompt = promptRaw instanceof String s ? s : promptRaw.toString();
        String systemPrompt = root.get("systemPrompt") instanceof String s ? s : null;
        Integer maxTurns = root.get("maxTurns") instanceof Number n ? n.intValue() : null;
        Integer toolTimeoutSeconds = root.get("toolTimeoutSeconds") instanceof Number n ? n.intValue() : null;
        return new RunAgentRequest(prompt, systemPrompt, maxTurns, toolTimeoutSeconds);
    }

    public static RemoteToolRegistrationRequest decodeRemoteToolRegistrationRequest(String json) {
        try {
            Map<String, Object> root = JsonReader.parseObject(json);
            return new RemoteToolRegistrationRequest(
                    stringValue(root.get("name")),
                    stringValue(root.get("description")),
                    mapList(root.get("parameters")),
                    stringValue(root.get("callbackUrl")));
        } catch (IllegalArgumentException e) {
            throw new HttpApiException(400, "invalid_params", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public static WorkflowStartRequest decodeWorkflowStartRequest(String json) {
        try {
            Map<String, Object> root = JsonReader.parseObject(json);
            Object workflowNameRaw = root.get("workflowName");
            if (!(workflowNameRaw instanceof String workflowName) || workflowName.isBlank()) {
                throw new HttpApiException(400, "invalid_params", "Missing required field: workflowName");
            }
            Object inputRaw = root.get("input");
            Map<String, Object> input = inputRaw instanceof Map<?, ?> map
                    ? (Map<String, Object>) map
                    : Map.of();
            return new WorkflowStartRequest(workflowName, input);
        } catch (HttpApiException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new HttpApiException(400, "invalid_params", e.getMessage());
        }
    }

    public static ServiceInvocationRequest decodeServiceInvocationRequest(String json) {
        try {
            Map<String, Object> root = JsonReader.parseObject(json);
            Object rawArgs = root.get("args");
            if (!(rawArgs instanceof List<?> args)) {
                throw new HttpApiException(400, "invalid_params", "Missing or invalid required field: args");
            }
            return new ServiceInvocationRequest(new ArrayList<>(args));
        } catch (HttpApiException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new HttpApiException(400, "invalid_params", e.getMessage());
        }
    }

    public static RemoteToolInvocationResponse decodeRemoteToolInvocationResponse(String json) {
        Map<String, Object> root = JsonReader.parseObject(json);
        Object successRaw = root.get("success");
        boolean success = successRaw instanceof Boolean b && b;
        return new RemoteToolInvocationResponse(
                success,
                stringValue(root.get("output")),
                stringValue(root.get("error")));
    }

    static String encodeJsonArray(List<?> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            appendJsonValue(out, values.get(i));
        }
        out.append(']');
        return out.toString();
    }

    static String encodeJsonValue(Object value) {
        StringBuilder out = new StringBuilder();
        appendJsonValue(out, value);
        return out.toString();
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void appendJsonValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String s) {
            appendJsonString(out, s);
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            out.append(JsonWriter.fromMap((Map<String, Object>) map));
        } else if (value instanceof List<?> list) {
            out.append(encodeJsonArray(list));
        } else {
            appendJsonString(out, value.toString());
        }
    }

    private static void appendJsonString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
