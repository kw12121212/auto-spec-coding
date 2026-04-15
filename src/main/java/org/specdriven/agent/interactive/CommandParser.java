package org.specdriven.agent.interactive;

/**
 * Parses raw interactive session input into a typed {@link ParsedCommand}.
 */
public interface CommandParser {

    /**
     * Parses the given input string into a bounded command.
     *
     * @param input non-null, non-blank input string
     * @return the parsed command (never null)
     * @throws IllegalArgumentException if input is null or blank
     */
    ParsedCommand parse(String input);
}
