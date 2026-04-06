package org.specdriven.agent.agent;

/**
 * A message originating from the human user.
 */
public record UserMessage(String content, long timestamp) implements Message {

    @Override
    public String role() {
        return "user";
    }
}
