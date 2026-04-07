package org.specdriven.agent.agent;

import java.util.List;
import java.util.Optional;

/**
 * Persistent storage for agent sessions, conversation history, and state snapshots.
 */
public interface SessionStore {

    /**
     * Persists the session. If {@code session.id()} is null, a UUID is generated and returned.
     *
     * @param session the session to save
     * @return the session ID (newly generated if input id was null)
     */
    String save(Session session);

    /**
     * Loads a session by ID.
     *
     * @param sessionId the session ID
     * @return the session, or empty if not found
     */
    Optional<Session> load(String sessionId);

    /**
     * Deletes a session and all its messages.
     *
     * @param sessionId the session ID
     */
    void delete(String sessionId);

    /**
     * Lists all sessions whose {@code expiryAt} is in the future.
     *
     * @return active sessions
     */
    List<Session> listActive();
}
