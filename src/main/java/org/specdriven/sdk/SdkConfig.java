package org.specdriven.sdk;

/**
 * SDK-level configuration record controlling agent behavior defaults.
 */
public record SdkConfig(
        int maxTurns,
        int toolTimeoutSeconds,
        String systemPrompt
) {
    public static SdkConfig defaults() {
        return new SdkConfig(50, 120, null);
    }
}
