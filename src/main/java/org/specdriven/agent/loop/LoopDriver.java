package org.specdriven.agent.loop;

import java.util.List;
import java.util.Optional;

/**
 * Control interface for the autonomous loop driver.
 */
public interface LoopDriver {

    /**
     * Starts the loop. Must be in IDLE state.
     *
     * @throws IllegalStateException if current state is not IDLE
     */
    void start();

    /**
     * Pauses the loop. Transitions from RECOMMENDING/RUNNING/CHECKPOINT to PAUSED.
     */
    void pause();

    /**
     * Resumes a paused loop. Transitions PAUSED to RECOMMENDING.
     */
    void resume();

    /**
     * Stops the loop. Transitions any non-STOPPED state to STOPPED.
     */
    void stop();

    /**
     * Returns the current loop state.
     */
    LoopState getState();

    /**
     * Returns the current iteration, if one is in progress.
     */
    Optional<LoopIteration> getCurrentIteration();

    /**
     * Returns completed iterations ordered by iteration number ascending.
     */
    List<LoopIteration> getCompletedIterations();

    /**
     * Returns the loop configuration.
     */
    LoopConfig getConfig();
}
