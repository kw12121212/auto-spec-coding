package org.specdriven.agent.agent;

/**
 * A message produced by the agent/LLM.
 */
public record AssistantMessage(String content, long timestamp) implements Message {

    @Override
    public String role() {
        return "assistant";
    }
}
