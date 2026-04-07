package org.specdriven.agent.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON parser for LLM provider response parsing.
 * Parses a JSON string into a {@code Map<String, Object>} tree where:
 * <ul>
 *   <li>JSON objects → {@code Map<String, Object>}</li>
 *   <li>JSON arrays  → {@code List<Object>}</li>
 *   <li>JSON strings → {@code String}</li>
 *   <li>JSON numbers → {@code Long} (integers) or {@code Double} (decimals)</li>
 *   <li>JSON booleans → {@code Boolean}</li>
 *   <li>JSON null    → {@code null}</li>
 * </ul>
 */
public final class JsonReader {

    private final String src;
    private int pos;

    private JsonReader(String src) {
        this.src = src;
        this.pos = 0;
    }

    /** Parse a JSON object string into a map. */
    public static Map<String, Object> parseObject(String json) {
        JsonReader r = new JsonReader(json.trim());
        Object result = r.parseValue();
        if (result instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) m;
            return typed;
        }
        throw new IllegalArgumentException("Expected JSON object, got: " + json);
    }

    /**
     * Extract a string value at a dot-separated path (e.g. "choices.0.message.content").
     * Returns null if any segment is missing.
     */
    public static String getString(Map<String, Object> root, String path) {
        Object value = getAt(root, path);
        return value instanceof String s ? s : (value != null ? value.toString() : null);
    }

    /** Extract a long value at a dot-separated path. Returns 0 if missing. */
    public static long getLong(Map<String, Object> root, String path) {
        Object value = getAt(root, path);
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
        }
        return 0L;
    }

    /**
     * Extract a list at a dot-separated path.
     * Returns an empty list if missing or not an array.
     */
    @SuppressWarnings("unchecked")
    public static List<Object> getList(Map<String, Object> root, String path) {
        Object value = getAt(root, path);
        return value instanceof List<?> l ? (List<Object>) l : List.of();
    }

    /**
     * Extract a nested map at a dot-separated path.
     * Returns an empty map if missing or not an object.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getMap(Map<String, Object> root, String path) {
        Object value = getAt(root, path);
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    // --- path navigation ---

    @SuppressWarnings("unchecked")
    private static Object getAt(Object node, String path) {
        if (node == null || path == null || path.isEmpty()) return node;
        int dot = path.indexOf('.');
        String segment = dot < 0 ? path : path.substring(0, dot);
        String rest = dot < 0 ? "" : path.substring(dot + 1);

        Object child;
        if (node instanceof Map<?, ?> m) {
            child = ((Map<String, Object>) m).get(segment);
        } else if (node instanceof List<?> l) {
            try {
                int idx = Integer.parseInt(segment);
                child = idx < l.size() ? l.get(idx) : null;
            } catch (NumberFormatException e) {
                return null;
            }
        } else {
            return null;
        }
        return rest.isEmpty() ? child : getAt(child, rest);
    }

    // --- recursive descent parser ---

    private Object parseValue() {
        skipWhitespace();
        if (pos >= src.length()) throw new IllegalArgumentException("Unexpected end of input");
        char c = src.charAt(pos);
        return switch (c) {
            case '{' -> parseObjectNode();
            case '[' -> parseArrayNode();
            case '"' -> parseString();
            case 't', 'f' -> parseBoolean();
            case 'n' -> parseNull();
            default  -> parseNumber();
        };
    }

    private Map<String, Object> parseObjectNode() {
        expect('{');
        Map<String, Object> map = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') { pos++; return map; }
        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            Object value = parseValue();
            map.put(key, value);
            skipWhitespace();
            char next = peek();
            if (next == '}') { pos++; break; }
            if (next == ',') { pos++; continue; }
            throw new IllegalArgumentException("Expected ',' or '}' at pos " + pos);
        }
        return map;
    }

    private List<Object> parseArrayNode() {
        expect('[');
        List<Object> list = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') { pos++; return list; }
        while (true) {
            skipWhitespace();
            list.add(parseValue());
            skipWhitespace();
            char next = peek();
            if (next == ']') { pos++; break; }
            if (next == ',') { pos++; continue; }
            throw new IllegalArgumentException("Expected ',' or ']' at pos " + pos);
        }
        return list;
    }

    private String parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                if (pos >= src.length()) break;
                char esc = src.charAt(pos++);
                switch (esc) {
                    case '"'  -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/'  -> sb.append('/');
                    case 'n'  -> sb.append('\n');
                    case 'r'  -> sb.append('\r');
                    case 't'  -> sb.append('\t');
                    case 'b'  -> sb.append('\b');
                    case 'f'  -> sb.append('\f');
                    case 'u'  -> {
                        String hex = src.substring(pos, Math.min(pos + 4, src.length()));
                        sb.append((char) Integer.parseInt(hex, 16));
                        pos += 4;
                    }
                    default -> sb.append(esc);
                }
            } else {
                sb.append(c);
            }
        }
        throw new IllegalArgumentException("Unterminated string");
    }

    private Boolean parseBoolean() {
        if (src.startsWith("true", pos))  { pos += 4; return Boolean.TRUE;  }
        if (src.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
        throw new IllegalArgumentException("Invalid boolean at pos " + pos);
    }

    private Object parseNull() {
        if (src.startsWith("null", pos)) { pos += 4; return null; }
        throw new IllegalArgumentException("Invalid null at pos " + pos);
    }

    private Number parseNumber() {
        int start = pos;
        if (pos < src.length() && src.charAt(pos) == '-') pos++;
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        boolean decimal = pos < src.length() && (src.charAt(pos) == '.' || src.charAt(pos) == 'e' || src.charAt(pos) == 'E');
        if (decimal) {
            if (src.charAt(pos) == '.') {
                pos++;
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            }
            if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                pos++;
                if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            }
            return Double.parseDouble(src.substring(start, pos));
        }
        return Long.parseLong(src.substring(start, pos));
    }

    private void skipWhitespace() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
    }

    private void expect(char c) {
        if (pos >= src.length() || src.charAt(pos) != c) {
            throw new IllegalArgumentException("Expected '" + c + "' at pos " + pos
                    + " but got '" + (pos < src.length() ? src.charAt(pos) : "EOF") + "'");
        }
        pos++;
    }

    private char peek() {
        return pos < src.length() ? src.charAt(pos) : '\0';
    }
}
