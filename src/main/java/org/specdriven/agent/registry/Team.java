package org.specdriven.agent.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An immutable team in the registry.
 *
 * @param id          unique identifier (null before first save, UUID after)
 * @param name        human-readable team name
 * @param description optional details
 * @param status      current lifecycle state
 * @param metadata    flexible key-value data
 * @param createdAt   epoch millis when the team was created
 * @param updatedAt   epoch millis when the team was last modified
 */
public record Team(
        String id,
        String name,
        String description,
        TeamStatus status,
        Map<String, Object> metadata,
        long createdAt,
        long updatedAt
) {
    public Team {
        metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
