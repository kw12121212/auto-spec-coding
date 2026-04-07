package org.specdriven.agent.json;

import java.util.List;
import java.util.Map;

/**
 * Minimal JSON serializer using StringBuilder. No external dependencies.
 * Handles the flat structures needed by LLM provider request serialization.
 */
public final class JsonWriter {

    private final StringBuilder sb = new StringBuilder();

    public static JsonWriter object() {
        JsonWriter w = new JsonWriter();
        w.sb.append('{');
        return w;
    }

    public static JsonWriter array() {
        JsonWriter w = new JsonWriter();
        w.sb.append('[');
        return w;
    }

    /** Append a string field. Value may be null (written as JSON null). */
    public JsonWriter field(String key, String value) {
        comma();
        appendString(key);
        sb.append(':');
        if (value == null) sb.append("null");
        else appendString(value);
        return this;
    }

    /** Append a long field. */
    public JsonWriter field(String key, long value) {
        comma();
        appendString(key);
        sb.append(':').append(value);
        return this;
    }

    /** Append a double field. */
    public JsonWriter field(String key, double value) {
        comma();
        appendString(key);
        sb.append(':').append(value);
        return this;
    }

    /** Append a boolean field. */
    public JsonWriter field(String key, boolean value) {
        comma();
        appendString(key);
        sb.append(':').append(value);
        return this;
    }

    /** Append a nested object or array field from a pre-built JSON string. */
    public JsonWriter rawField(String key, String rawJson) {
        comma();
        appendString(key);
        sb.append(':').append(rawJson);
        return this;
    }

    /** Append an array field from a list of pre-built JSON strings. */
    public JsonWriter arrayField(String key, List<String> items) {
        comma();
        appendString(key);
        sb.append(":[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(items.get(i));
        }
        sb.append(']');
        return this;
    }

    /** Append a raw JSON element (for array writers). */
    public JsonWriter element(String rawJson) {
        comma();
        sb.append(rawJson);
        return this;
    }

    /** Finalize and return the JSON string. */
    public String build() {
        char opener = sb.charAt(0);
        if (sb.length() == 1) {
            return opener == '{' ? "{}" : "[]";
        }
        return sb.toString() + (opener == '{' ? '}' : ']');
    }

    /**
     * Serialize a {@code Map<String, Object>} into a JSON object string.
     * Supports nested maps, lists, strings, numbers, booleans, and nulls.
     */
    public static String fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) out.append(',');
            first = false;
            appendStringTo(out, entry.getKey());
            out.append(':');
            appendValueTo(out, entry.getValue());
        }
        out.append('}');
        return out.toString();
    }

    // --- private helpers ---

    private void comma() {
        char last = sb.charAt(sb.length() - 1);
        if (last != '{' && last != '[') sb.append(',');
    }

    private void appendString(String value) {
        appendStringTo(sb, value);
    }

    static void appendStringTo(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"'  -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default   -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }

    @SuppressWarnings("unchecked")
    static void appendValueTo(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String s) {
            appendStringTo(out, s);
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Map<?, ?> m) {
            out.append(fromMap((Map<String, Object>) m));
        } else if (value instanceof List<?> list) {
            out.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) out.append(',');
                first = false;
                appendValueTo(out, item);
            }
            out.append(']');
        } else {
            appendStringTo(out, value.toString());
        }
    }
}
