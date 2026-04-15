package org.specdriven.agent.interactive;

/**
 * Sealed type hierarchy representing the result of parsing interactive session input.
 * Each subtype corresponds to a bounded action the system can take.
 */
public sealed interface ParsedCommand
        permits AnswerCommand, ShowCommand, HelpCommand, ExitCommand, UnknownCommand {

    /**
     * Returns the raw input string that was parsed to produce this command.
     */
    String originalInput();
}
