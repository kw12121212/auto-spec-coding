package org.specdriven.agent.event;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An immutable structured event in the agent system.
 *
 * @param type      the event type
 * @param timestamp epoch millis when the event occurred
 * @param source    the origin of the event (e.g. tool name, agent id)
 * @param metadata  additional key-value data attached to the event
 */
public record Event(
        EventType type,
        long timestamp,
        String source,
        Map<String, Object> metadata
) {
    public Event {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * Serializes this event to a JSON string.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"").append(type.name()).append("\"");
        sb.append(",\"timestamp\":").append(timestamp);
        sb.append(",\"source\":").append(escapeJson(source));
        sb.append(",\"metadata\":").append(metadataToJson(metadata));
        sb.append("}");
        return sb.toString();
    }

    /**
     * Reconstructs an Event from its JSON representation.
     */
    public static Event fromJson(String json) {
        String s = json.trim();
        if (!s.startsWith("{") || !s.endsWith("}")) {
            throw new IllegalArgumentException("Invalid JSON: must be an object");
        }
        s = s.substring(1, s.length() - 1).trim();

        EventType type = null;
        long timestamp = 0;
        String source = null;
        Map<String, Object> metadata = null;

        // Parse top-level fields using simple key extraction
        String remaining = s;
        while (!remaining.isEmpty()) {
            remaining = remaining.stripLeading();
            if (!remaining.startsWith("\"")) break;

            int keyEnd = findClosingQuote(remaining, 1);
            if (keyEnd < 0) break;
            String key = unescapeJson(remaining.substring(1, keyEnd));
            remaining = remaining.substring(keyEnd + 1).stripLeading();
            if (!remaining.startsWith(":")) break;
            remaining = remaining.substring(1).stripLeading();

            Object value;
            if (remaining.startsWith("\"")) {
                int valEnd = findClosingQuote(remaining, 1);
                if (valEnd < 0) break;
                String raw = remaining.substring(1, valEnd);
                value = unescapeJson(raw);
                remaining = remaining.substring(valEnd + 1);
            } else if (remaining.startsWith("{")) {
                int objEnd = findClosingBrace(remaining, 0);
                if (objEnd < 0) break;
                String obj = remaining.substring(0, objEnd + 1);
                value = parseMetadataObject(obj);
                remaining = remaining.substring(objEnd + 1);
            } else if (remaining.startsWith("true") && !isAlphaNum(remaining, 4)) {
                value = Boolean.TRUE;
                remaining = remaining.substring(4);
            } else if (remaining.startsWith("false") && !isAlphaNum(remaining, 5)) {
                value = Boolean.FALSE;
                remaining = remaining.substring(5);
            } else if (remaining.startsWith("null") && !isAlphaNum(remaining, 4)) {
                value = null;
                remaining = remaining.substring(4);
            } else {
                // number
                int numEnd = 0;
                while (numEnd < remaining.length() && isNumberChar(remaining.charAt(numEnd))) {
                    numEnd++;
                }
                if (numEnd == 0) break;
                String numStr = remaining.substring(0, numEnd);
                value = parseNumber(numStr);
                remaining = remaining.substring(numEnd);
            }

            try {
                switch (key) {
                    case "type" -> type = EventType.valueOf((String) value);
                    case "timestamp" -> timestamp = ((Number) value).longValue();
                    case "source" -> source = (String) value;
                    case "metadata" -> metadata = (Map<String, Object>) value;
                }
            } catch (ClassCastException e) {
                throw new IllegalArgumentException("Invalid type for field '" + key + "'", e);
            }

            remaining = remaining.stripLeading();
            if (remaining.startsWith(",")) {
                remaining = remaining.substring(1);
            }
        }

        if (type == null) throw new IllegalArgumentException("Missing 'type' field");
        if (source == null) throw new IllegalArgumentException("Missing 'source' field");
        return new Event(type, timestamp, source, metadata != null ? metadata : Map.of());
    }

    private static String metadataToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append(escapeJson(entry.getKey())).append(":");
            sb.append(valueToJson(entry.getValue()));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private static String valueToJson(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return escapeJson(s);
        if (value instanceof Boolean b) return b.toString();
        if (value instanceof Number n) return n.toString();
        return escapeJson(value.toString());
    }

    private static String escapeJson(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    private static String unescapeJson(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case '"' -> { sb.append('"'); i++; }
                    case '\\' -> { sb.append('\\'); i++; }
                    case 'n' -> { sb.append('\n'); i++; }
                    case 'r' -> { sb.append('\r'); i++; }
                    case 't' -> { sb.append('\t'); i++; }
                    default -> sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static int findClosingQuote(String s, int start) {
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) { i++; continue; }
            if (c == '"') return i;
        }
        return -1;
    }

    private static int findClosingBrace(String s, int openPos) {
        int depth = 0;
        boolean inString = false;
        for (int i = openPos; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (c == '\\' && i + 1 < s.length()) { i++; continue; }
                if (c == '"') inString = false;
            } else {
                if (c == '"') inString = true;
                else if (c == '{') depth++;
                else if (c == '}') { depth--; if (depth == 0) return i; }
            }
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseMetadataObject(String json) {
        if (json.equals("{}")) return Map.of();
        String inner = json.substring(1, json.length() - 1).trim();
        Map<String, Object> result = new LinkedHashMap<>();
        String remaining = inner;
        while (!remaining.isEmpty()) {
            remaining = remaining.stripLeading();
            if (!remaining.startsWith("\"")) break;
            int keyEnd = findClosingQuote(remaining, 1);
            if (keyEnd < 0) break;
            String key = unescapeJson(remaining.substring(1, keyEnd));
            remaining = remaining.substring(keyEnd + 1).stripLeading();
            if (!remaining.startsWith(":")) break;
            remaining = remaining.substring(1).stripLeading();

            Object value;
            if (remaining.startsWith("\"")) {
                int valEnd = findClosingQuote(remaining, 1);
                if (valEnd < 0) break;
                value = unescapeJson(remaining.substring(1, valEnd));
                remaining = remaining.substring(valEnd + 1);
            } else if (remaining.startsWith("true") && !isAlphaNum(remaining, 4)) {
                value = Boolean.TRUE;
                remaining = remaining.substring(4);
            } else if (remaining.startsWith("false") && !isAlphaNum(remaining, 5)) {
                value = Boolean.FALSE;
                remaining = remaining.substring(5);
            } else if (remaining.startsWith("null") && !isAlphaNum(remaining, 4)) {
                value = null;
                remaining = remaining.substring(4);
            } else {
                int numEnd = 0;
                while (numEnd < remaining.length() && isNumberChar(remaining.charAt(numEnd))) {
                    numEnd++;
                }
                if (numEnd == 0) break;
                value = parseNumber(remaining.substring(0, numEnd));
                remaining = remaining.substring(numEnd);
            }

            result.put(key, value);
            remaining = remaining.stripLeading();
            if (remaining.startsWith(",")) remaining = remaining.substring(1);
        }
        return Collections.unmodifiableMap(result);
    }

    private static boolean isNumberChar(char c) {
        return (c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E';
    }

    private static boolean isAlphaNum(String s, int offset) {
        if (offset >= s.length()) return false;
        char c = s.charAt(offset);
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
    }

    private static Number parseNumber(String s) {
        if (s.contains(".") || s.contains("e") || s.contains("E")) {
            return Double.parseDouble(s);
        }
        long v = Long.parseLong(s);
        if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) return (int) v;
        return v;
    }
}
