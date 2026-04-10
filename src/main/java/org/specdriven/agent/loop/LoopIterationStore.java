package org.specdriven.agent.loop;

import java.util.List;
import java.util.Optional;

/**
 * Persistence interface for loop iteration records and progress snapshots.
 */
public interface LoopIterationStore {

    /**
     * Persists a completed iteration record.
     */
    void saveIteration(LoopIteration iteration);

    /**
     * Returns all stored iterations ordered by iterationNumber ascending.
     */
    List<LoopIteration> loadIterations();

    /**
     * Persists the current loop-level progress snapshot.
     */
    void saveProgress(LoopProgress progress);

    /**
     * Returns the most recent progress snapshot, or empty if none exists.
     */
    Optional<LoopProgress> loadProgress();

    /**
     * Removes all stored iterations and progress. Intended for test cleanup.
     */
    void clear();
}
