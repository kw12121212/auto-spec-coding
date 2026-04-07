package org.specdriven.agent.registry;

import java.util.List;
import java.util.Optional;

/**
 * Persistence and scheduling contract for cron entries.
 */
public interface CronStore {

    /**
     * Creates a new cron entry. If the entry has no ID, a UUID is generated.
     * Computes and persists the initial {@code nextFireTime}.
     *
     * @return the entry ID
     */
    String create(CronEntry entry);

    /**
     * Loads a cron entry by ID.
     */
    Optional<CronEntry> load(String entryId);

    /**
     * Cancels an active cron entry.
     *
     * @throws java.util.NoSuchElementException if the entry does not exist
     * @throws IllegalStateException if the entry is not ACTIVE
     */
    void cancel(String entryId);

    /**
     * Returns all non-cancelled entries ordered by createdAt ascending.
     */
    List<CronEntry> list();

    /**
     * Returns entries matching the given status.
     */
    List<CronEntry> queryByStatus(CronStatus status);
}
