package org.specdriven.agent.agent;

/**
 * Sealed interface representing a message in an agent conversation.
 * Each subtype identifies the origin of the message via {@link #role()}.
 */
public sealed interface Message
    permits UserMessage, AssistantMessage, ToolMessage, SystemMessage {

    /**
     * Returns the message origin: "user", "assistant", "tool", or "system".
     */
    String role();

    /**
     * Returns the text content of this message.
     */
    String content();

    /**
     * Returns the epoch millis timestamp when this message was created.
     */
    long timestamp();
}
