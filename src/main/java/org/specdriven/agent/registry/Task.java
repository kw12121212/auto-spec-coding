package org.specdriven.agent.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An immutable task in the registry.
 *
 * @param id          unique identifier (null before first save, UUID after)
 * @param title       human-readable title
 * @param description optional details
 * @param status      current lifecycle state
 * @param owner       optional owner (agent ID, user ID, or team name)
 * @param parentTaskId optional parent task for hierarchy
 * @param metadata    flexible key-value data
 * @param createdAt   epoch millis when the task was created
 * @param updatedAt   epoch millis when the task was last modified
 */
public record Task(
        String id,
        String title,
        String description,
        TaskStatus status,
        String owner,
        String parentTaskId,
        Map<String, Object> metadata,
        long createdAt,
        long updatedAt
) {
    public Task {
        metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
