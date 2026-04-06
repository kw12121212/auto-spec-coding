package org.specdriven.agent.event;

import java.util.Map;

/**
 * An immutable structured event in the agent system.
 *
 * @param type      the event type
 * @param timestamp epoch millis when the event occurred
 * @param source    the origin of the event (e.g. tool name, agent id)
 * @param metadata  additional key-value data attached to the event
 */
public record Event(
        EventType type,
        long timestamp,
        String source,
        Map<String, Object> metadata
) {}
