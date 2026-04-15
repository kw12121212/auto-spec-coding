package org.specdriven.agent.interactive;

import java.util.Objects;

/**
 * Parsed command representing a SHOW query for system state.
 *
 * @param originalInput the raw input that was parsed
 * @param showType      the type of information to show
 */
public record ShowCommand(String originalInput, ShowType showType) implements ParsedCommand {

    public ShowCommand {
        Objects.requireNonNull(originalInput, "originalInput");
        Objects.requireNonNull(showType, "showType");
    }
}
