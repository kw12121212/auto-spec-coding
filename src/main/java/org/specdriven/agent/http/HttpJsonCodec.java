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
}
