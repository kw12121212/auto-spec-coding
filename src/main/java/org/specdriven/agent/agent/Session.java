package org.specdriven.agent.agent;

/**
 * Snapshot of an agent session — id, lifecycle state, timestamps, and conversation history.
 * {@code expiryAt} is set to {@code createdAt + 30 days} on creation and must not change across saves.
 * {@code id} may be null before first save; {@link SessionStore#save} assigns a UUID when null.
 */
public record Session(
        String id,
        AgentState state,
        long createdAt,
        long updatedAt,
        long expiryAt,
        Conversation conversation
) {
    static final long TTL_MS = 30L * 24 * 3600 * 1000L;

    /** Creates a new session with null id and 30-day TTL from now. */
    static Session create(AgentState state, Conversation conversation) {
        long now = System.currentTimeMillis();
        return new Session(null, state, now, now, now + TTL_MS, conversation);
    }
}
