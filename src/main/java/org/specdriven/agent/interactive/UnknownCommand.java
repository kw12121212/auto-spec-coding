package org.specdriven.agent.interactive;

import java.util.Objects;

/**
 * Parsed command representing unrecognized input.
 *
 * @param originalInput the raw input that could not be matched to a known command
 */
public record UnknownCommand(String originalInput) implements ParsedCommand {

    public UnknownCommand {
        Objects.requireNonNull(originalInput, "originalInput");
    }
}
