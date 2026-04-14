package org.specdriven.agent.loop;

import org.specdriven.agent.interactive.InteractiveSession;

/**
 * Factory for creating interactive sessions during loop human-escalation pauses.
 *
 * <p>Implementations MUST return a non-null {@link InteractiveSession} in {@code NEW} state.
 * Factory failures MUST be captured in the returned session (which enters {@code ERROR} state
 * on {@link InteractiveSession#start()}), rather than thrown as checked exceptions.
 */
@FunctionalInterface
public interface InteractiveSessionFactory {

    /**
     * Creates a new interactive session for the given session ID.
     *
     * @param sessionId non-null, non-blank identifier for the session
     * @return a non-null InteractiveSession in NEW state
     */
    InteractiveSession create(String sessionId);
}
