package org.specdriven.agent.agent;

import java.util.Map;

/**
 * Configuration for the orchestrator loop.
 */
public record OrchestratorConfig(
        int maxTurns,
        int toolTimeoutSeconds
) {
    private static final OrchestratorConfig DEFAULTS = new OrchestratorConfig(50, 120);

    public OrchestratorConfig {
        if (maxTurns <= 0) throw new IllegalArgumentException("maxTurns must be positive");
        if (toolTimeoutSeconds <= 0) throw new IllegalArgumentException("toolTimeoutSeconds must be positive");
    }

    /**
     * Returns the default configuration (50 turns, 120s timeout).
     */
    public static OrchestratorConfig defaults() {
        return DEFAULTS;
    }

    /**
     * Creates a config from a key-value map, falling back to defaults for missing keys.
     */
    public static OrchestratorConfig fromMap(Map<String, String> config) {
        if (config == null) return defaults();
        int turns = parseInt(config, "orchestrator.maxTurns", DEFAULTS.maxTurns());
        int timeout = parseInt(config, "orchestrator.toolTimeoutSeconds", DEFAULTS.toolTimeoutSeconds());
        return new OrchestratorConfig(turns, timeout);
    }

    private static int parseInt(Map<String, String> config, String key, int defaultValue) {
        String value = config.get(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
