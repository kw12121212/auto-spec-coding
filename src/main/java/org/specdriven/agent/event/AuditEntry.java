package org.specdriven.agent.event;

/**
 * A persisted audit entry wrapping an {@link Event} with its auto-generated database ID.
 *
 * @param id    the database-generated row ID
 * @param event the original event
 */
public record AuditEntry(long id, Event event) {
}
