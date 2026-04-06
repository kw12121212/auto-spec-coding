package org.specdriven.agent.agent;

/**
 * A message representing the result of a tool execution.
 */
public record ToolMessage(String content, long timestamp, String toolName) implements Message {

    @Override
    public String role() {
        return "tool";
    }
}
