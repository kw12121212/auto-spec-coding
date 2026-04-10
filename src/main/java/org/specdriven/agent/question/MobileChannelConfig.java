package org.specdriven.agent.question;

import java.util.Collections;
import java.util.Map;

/**
 * Immutable configuration for a single mobile interaction channel.
 * Stores a channel type name, a vault key reference for credentials,
 * and optional channel-specific overrides.
 */
public record MobileChannelConfig(
        String channelType,
        String vaultKey,
        Map<String, String> overrides
) {
    public MobileChannelConfig {
        if (channelType == null || channelType.isBlank()) {
            throw new IllegalArgumentException("channelType must not be null or blank");
        }
        if (vaultKey == null || vaultKey.isBlank()) {
            throw new IllegalArgumentException("vaultKey must not be null or blank");
        }
        overrides = overrides == null ? Collections.emptyMap() : Map.copyOf(overrides);
    }

    public MobileChannelConfig(String channelType, String vaultKey) {
        this(channelType, vaultKey, Collections.emptyMap());
    }
}
