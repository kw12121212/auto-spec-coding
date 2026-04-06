package org.specdriven.agent.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable configuration facade over a parsed YAML structure.
 * Provides typed accessors, dot-notation nested access, section views, and flattening.
 */
public final class Config {

    private final Map<String, Object> data;
    private final String sourceDescription;

    Config(Map<String, Object> data, String sourceDescription) {
        this.data = Collections.unmodifiableMap(new LinkedHashMap<>(data));
        this.sourceDescription = sourceDescription;
    }

    /**
     * Get a string value by dot-notation key, throwing ConfigException if missing.
     */
    public String getString(String key) {
        Object value = resolve(key);
        if (value == null) {
            throw new ConfigException("Missing required config key '" + key + "' in " + sourceDescription);
        }
        return value.toString();
    }

    /**
     * Get a string value by dot-notation key, returning defaultValue if missing.
     */
    public String getString(String key, String defaultValue) {
        Object value = resolve(key);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * Get an integer value by dot-notation key, returning defaultValue if missing.
     */
    public int getInt(String key, int defaultValue) {
        Object value = resolve(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            throw new ConfigException("Config key '" + key + "' is not a valid integer: " + value, e);
        }
    }

    /**
     * Get a boolean value by dot-notation key, returning defaultValue if missing.
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = resolve(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    /**
     * Return a sub-Config scoped to the nested section under the given prefix.
     */
    @SuppressWarnings("unchecked")
    public Config getSection(String prefix) {
        Object value = resolve(prefix);
        if (value == null) {
            throw new ConfigException("Missing config section '" + prefix + "' in " + sourceDescription);
        }
        if (!(value instanceof Map)) {
            throw new ConfigException("Config key '" + prefix + "' is not a section in " + sourceDescription);
        }
        return new Config((Map<String, Object>) value, sourceDescription + " [" + prefix + "]");
    }

    /**
     * Flatten the entire config tree into a Map with dot-notation string keys and string values.
     * Compatible with {@code Agent.init(Map<String, String>)}.
     */
    public Map<String, String> asMap() {
        Map<String, String> flat = new LinkedHashMap<>();
        flatten("", data, flat);
        return Collections.unmodifiableMap(flat);
    }

    @Override
    public String toString() {
        return "Config(" + sourceDescription + ")";
    }

    // --- internal ---

    @SuppressWarnings("unchecked")
    private Object resolve(String key) {
        Objects.requireNonNull(key, "Config key must not be null");
        String[] parts = key.split("\\.");
        Map<String, Object> current = data;
        for (int i = 0; i < parts.length; i++) {
            Object value = current.get(parts[i]);
            if (value == null) return null;
            if (i == parts.length - 1) return value;
            if (!(value instanceof Map)) return null;
            current = (Map<String, Object>) value;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void flatten(String prefix, Map<String, Object> source, Map<String, String> target) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String fullKey = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                flatten(fullKey, (Map<String, Object>) value, target);
            } else {
                target.put(fullKey, value != null ? value.toString() : "");
            }
        }
    }
}
