package org.specdriven.agent.interactive;

import java.util.Objects;

/**
 * Parsed command representing an answer submission to a waiting question.
 *
 * @param originalInput the raw input that was parsed
 * @param answerText    the extracted answer content (non-blank)
 */
public record AnswerCommand(String originalInput, String answerText) implements ParsedCommand {

    public AnswerCommand {
        Objects.requireNonNull(originalInput, "originalInput");
        if (answerText == null || answerText.isBlank()) {
            throw new IllegalArgumentException("answerText must not be blank");
        }
    }
}
