package org.specdriven.agent.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An immutable cron entry in the registry.
 *
 * @param id            unique identifier (null before first save, UUID after)
 * @param name          human-readable name
 * @param cronExpression 5-field cron expression (null for one-shot entries)
 * @param delayMillis   delay for one-shot entries (0 for recurring)
 * @param status        current lifecycle state
 * @param prompt        the prompt to execute when the entry fires
 * @param metadata      flexible key-value data
 * @param createdAt     epoch millis when the entry was created
 * @param updatedAt     epoch millis when the entry was last modified
 * @param nextFireTime  epoch millis of the next scheduled fire
 * @param lastFireTime  epoch millis of the last fire (0 if never fired)
 */
public record CronEntry(
        String id,
        String name,
        String cronExpression,
        long delayMillis,
        CronStatus status,
        String prompt,
        Map<String, Object> metadata,
        long createdAt,
        long updatedAt,
        long nextFireTime,
        long lastFireTime
) {
    public CronEntry {
        metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
