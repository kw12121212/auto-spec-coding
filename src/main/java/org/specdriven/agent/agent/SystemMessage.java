package org.specdriven.agent.agent;

/**
 * A system-level instruction message.
 */
public record SystemMessage(String content, long timestamp) implements Message {

    @Override
    public String role() {
        return "system";
    }
}
