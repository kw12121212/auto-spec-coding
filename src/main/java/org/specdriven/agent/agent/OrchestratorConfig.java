package org.specdriven.agent.agent;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.specdriven.agent.hook.ToolExecutionHook;

/**
 * Configuration for the orchestrator loop.
 */
public record OrchestratorConfig(
        int maxTurns,
        int toolTimeoutSeconds,
        int questionTimeoutSeconds,
        List<ToolExecutionHook> hooks
) {
    private static final OrchestratorConfig DEFAULTS = new OrchestratorConfig(50, 120, 300, Collections.emptyList());

    public OrchestratorConfig {
        if (maxTurns <= 0) throw new IllegalArgumentException("maxTurns must be positive");
        if (toolTimeoutSeconds <= 0) throw new IllegalArgumentException("toolTimeoutSeconds must be positive");
        if (questionTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("questionTimeoutSeconds must be positive");
        }
        if (hooks == null) throw new IllegalArgumentException("hooks must not be null");
    }

    public OrchestratorConfig(int maxTurns, int toolTimeoutSeconds, List<ToolExecutionHook> hooks) {
        this(maxTurns, toolTimeoutSeconds, DEFAULTS.questionTimeoutSeconds(), hooks);
    }

    /**
     * Convenience constructor without hooks (defaults to empty list).
     */
    public OrchestratorConfig(int maxTurns, int toolTimeoutSeconds) {
        this(maxTurns, toolTimeoutSeconds, DEFAULTS.questionTimeoutSeconds(), Collections.emptyList());
    }

    /**
     * Returns the default configuration (50 turns, 120s timeout, no hooks).
     */
    public static OrchestratorConfig defaults() {
        return DEFAULTS;
    }

    /**
     * Creates a config from a key-value map, falling back to defaults for missing keys.
     * Hooks are not configurable from the map — use the constructor directly.
     */
    public static OrchestratorConfig fromMap(Map<String, String> config) {
        if (config == null) return defaults();
        int turns = parseInt(config, "maxTurns",
                parseInt(config, "orchestrator.maxTurns", DEFAULTS.maxTurns()));
        int timeout = parseInt(config, "toolTimeoutSeconds",
                parseInt(config, "orchestrator.toolTimeoutSeconds", DEFAULTS.toolTimeoutSeconds()));
        int questionTimeout = parseInt(config, "questionTimeoutSeconds",
                parseInt(config, "orchestrator.questionTimeoutSeconds", DEFAULTS.questionTimeoutSeconds()));
        return new OrchestratorConfig(turns, timeout, questionTimeout, Collections.emptyList());
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
