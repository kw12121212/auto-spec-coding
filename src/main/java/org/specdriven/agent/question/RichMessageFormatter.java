package org.specdriven.agent.question;

/**
 * Extension point for formatting question payloads into channel-specific message text.
 * Implementations can provide rich formatting (Markdown, embeds) per channel.
 */
@FunctionalInterface
public interface RichMessageFormatter {

    /**
     * Format a question into a message string for the target channel.
     *
     * @param question the question to format
     * @return formatted message text
     */
    String format(Question question);
}
