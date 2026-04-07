package org.specdriven.agent.event;

import java.util.List;

/**
 * Persistent storage and query interface for audit log entries.
 */
public interface AuditLogStore {

    /**
     * Persists an event and returns the generated entry ID.
     *
     * @param event the event to persist
     * @return the auto-generated row ID
     */
    long save(Event event);

    /**
     * Queries audit entries by event type within a time range (inclusive).
     *
     * @param type          the event type to match
     * @param fromTimestamp start of the time range (inclusive)
     * @param toTimestamp   end of the time range (inclusive)
     * @return matching entries ordered by timestamp ascending
     */
    List<AuditEntry> query(EventType type, long fromTimestamp, long toTimestamp);

    /**
     * Queries audit entries by source within a time range (inclusive).
     *
     * @param source        the event source to match
     * @param fromTimestamp start of the time range (inclusive)
     * @param toTimestamp   end of the time range (inclusive)
     * @return matching entries ordered by timestamp ascending
     */
    List<AuditEntry> queryBySource(String source, long fromTimestamp, long toTimestamp);

    /**
     * Deletes entries older than the given cutoff timestamp.
     *
     * @param cutoffTimestamp entries with event_ts before this value are deleted
     * @return the number of deleted rows
     */
    int deleteOlderThan(long cutoffTimestamp);

    /**
     * Returns the total number of persisted entries.
     *
     * @return total entry count
     */
    long count();
}
