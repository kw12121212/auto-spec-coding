package org.specdriven.agent.interactive;

import java.util.List;

/**
 * Minimum public contract for an interactive agent session.
 *
 * <p>Lifecycle: {@code NEW -> ACTIVE -> CLOSED}. {@code ERROR} is a terminal failure state
 * that also transitions to {@code CLOSED} on {@link #close()}.
 *
 * <p>This interface is the stable boundary that later adapter and bridge changes will implement;
 * it deliberately carries no dependency on Lealone, {@code DefaultLoopDriver}, or
 * {@code QuestionDeliveryService}.
 */
public interface InteractiveSession {

    /**
     * Returns the stable identifier for this session. Never null or blank.
     */
    String sessionId();

    /**
     * Returns the current lifecycle state.
     */
    InteractiveSessionState state();

    /**
     * Starts the session, transitioning from {@code NEW} to {@code ACTIVE}.
     *
     * @throws IllegalStateException if the current state is not {@code NEW}
     */
    void start();

    /**
     * Submits a line of input to the session.
     *
     * @param input non-null, non-blank input string
     * @throws IllegalArgumentException if {@code input} is null or blank
     * @throws IllegalStateException    if the current state is not {@code ACTIVE}
     */
    void submit(String input);

    /**
     * Drains and returns all pending output messages in emission order,
     * then clears the pending buffer. Returns an empty list when no output is pending.
     */
    List<String> drainOutput();

    /**
     * Closes the session, transitioning to {@code CLOSED}.
     * Idempotent: calling {@code close()} when already {@code CLOSED} is a no-op.
     */
    void close();
}
