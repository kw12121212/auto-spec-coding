package org.specdriven.agent.jsonrpc;

import org.specdriven.agent.json.JsonReader;
import org.specdriven.agent.json.JsonWriter;

import java.util.List;
import java.util.Map;

/**
 * Codec for encoding and decoding JSON-RPC 2.0 protocol types.
 * Uses the project's existing {@link JsonReader}/{@link JsonWriter} utilities.
 */
public final class JsonRpcCodec {

    private JsonRpcCodec() {}

    // --- Encoding ---

    /** Encode a {@link JsonRpcResponse} to a JSON string. */
    public static String encode(JsonRpcResponse response) {
        JsonWriter w = JsonWriter.object()
                .field("jsonrpc", "2.0");
        // Write id with correct type
        writeId(w, response.id());
        if (response.isSuccess()) {
            w.rawField("result", encodeValue(response.result()));
        } else {
            w.rawField("error", encodeError(response.error()));
        }
        return w.build();
    }

    /** Encode a {@link JsonRpcNotification} to a JSON string. No id field. */
    public static String encode(JsonRpcNotification notification) {
        JsonWriter w = JsonWriter.object()
                .field("jsonrpc", "2.0")
                .field("method", notification.method());
        Object params = notification.params();
        if (params == null) {
            w.rawField("params", "{}");
        } else if (params instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) m;
            w.rawField("params", JsonWriter.fromMap(typed));
        } else if (params instanceof List<?> list) {
            w.rawField("params", encodeList(list));
        } else {
            w.rawField("params", encodeValue(params));
        }
        return w.build();
    }

    // --- Decoding ---

    /**
     * Decode a JSON string into a {@link JsonRpcRequest} or {@link JsonRpcNotification}.
     * Returns {@link JsonRpcRequest} if the JSON contains an "id" field,
     * or {@link JsonRpcNotification} if "id" is absent.
     *
     * @throws JsonRpcProtocolException for protocol violations
     */
    public static Object decodeRequest(String json) {
        Map<String, Object> root;
        try {
            root = JsonReader.parseObject(json);
        } catch (IllegalArgumentException e) {
            throw new JsonRpcProtocolException(-32700, "Parse error: " + e.getMessage(), e);
        }

        // Validate jsonrpc version
        Object version = root.get("jsonrpc");
        if (!"2.0".equals(version)) {
            throw new JsonRpcProtocolException(-32600,
                    "Invalid Request: missing or wrong jsonrpc version");
        }

        // Validate method
        Object methodObj = root.get("method");
        if (methodObj == null) {
            throw new JsonRpcProtocolException(-32600,
                    "Invalid Request: missing method");
        }
        if (!(methodObj instanceof String method)) {
            throw new JsonRpcProtocolException(-32600,
                    "Invalid Request: method must be a string");
        }

        // Extract params (default to empty map if absent)
        Object params = root.get("params");
        if (params == null) {
            params = Map.of();
        }

        // Detect request vs notification by presence of "id"
        if (root.containsKey("id")) {
            Object id = root.get("id");
            return new JsonRpcRequest(id, method, params);
        } else {
            return new JsonRpcNotification(method, params);
        }
    }

    // --- Private helpers ---

    private static void writeId(JsonWriter w, Object id) {
        if (id == null) {
            w.field("id", (String) null);
        } else if (id instanceof Long l) {
            w.field("id", l);
        } else if (id instanceof Number n) {
            w.field("id", n.longValue());
        } else {
            w.field("id", id.toString());
        }
    }

    private static String encodeError(JsonRpcError err) {
        JsonWriter w = JsonWriter.object()
                .field("code", err.code())
                .field("message", err.message());
        if (err.data() != null) {
            w.rawField("data", JsonWriter.fromMap(err.data()));
        }
        return w.build();
    }

    @SuppressWarnings("unchecked")
    private static String encodeValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) {
            // Wrap in quotes via JsonWriter
            return JsonWriter.object().field("v", s).build()
                    .replaceAll("^\\{\"v\":", "")
                    .replaceAll("}$", "");
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> m) {
            return JsonWriter.fromMap((Map<String, Object>) m);
        }
        if (value instanceof List<?> list) {
            return encodeList(list);
        }
        // Fallback: stringify
        return "\"" + value + "\"";
    }

    private static String encodeList(List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(encodeValue(list.get(i)));
        }
        sb.append(']');
        return sb.toString();
    }
}
