package org.specdriven.agent.interactive;

import java.util.Objects;

/**
 * Parsed command representing a request to exit the interactive session.
 *
 * @param originalInput the raw input that was parsed
 */
public record ExitCommand(String originalInput) implements ParsedCommand {

    public ExitCommand {
        Objects.requireNonNull(originalInput, "originalInput");
    }
}
