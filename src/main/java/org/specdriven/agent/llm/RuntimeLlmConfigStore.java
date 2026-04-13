package org.specdriven.agent.llm;

import org.specdriven.agent.agent.LlmConfigSnapshot;

import java.util.List;
import java.util.Optional;

/**
 * Persists the default non-sensitive runtime LLM config snapshot and its history.
 */
public interface RuntimeLlmConfigStore extends AutoCloseable {

    /**
     * Loads the currently active persisted default snapshot, if one exists.
     */
    Optional<LlmConfigSnapshot> loadDefaultSnapshot();

    /**
     * Persists a new default snapshot version and makes it active.
     */
    RuntimeLlmConfigVersion persistDefaultSnapshot(LlmConfigSnapshot snapshot);

    /**
     * Returns the persisted default snapshot history in descending version order.
     */
    List<RuntimeLlmConfigVersion> listDefaultSnapshotVersions();

    /**
     * Restores an earlier persisted version as the active default snapshot.
     */
    RuntimeLlmConfigVersion restoreDefaultSnapshot(long version);

    @Override
    default void close() {
    }
}
