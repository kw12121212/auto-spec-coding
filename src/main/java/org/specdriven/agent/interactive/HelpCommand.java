package org.specdriven.agent.interactive;

import java.util.Objects;

/**
 * Parsed command representing a request for available commands.
 *
 * @param originalInput the raw input that was parsed
 */
public record HelpCommand(String originalInput) implements ParsedCommand {

    public HelpCommand {
        Objects.requireNonNull(originalInput, "originalInput");
    }
}
