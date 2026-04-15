package org.specdriven.agent.loop;

import org.specdriven.agent.question.Question;

import java.util.Objects;

/**
 * Structured result for a single spec-driven pipeline phase.
 */
public record PhaseExecutionResult(
        IterationStatus status,
        String failureReason,
        long tokenUsage,
        Question question
) {

    public PhaseExecutionResult {
        Objects.requireNonNull(status, "status must not be null");
        if (tokenUsage < 0) {
            throw new IllegalArgumentException("tokenUsage must be non-negative");
        }
        if (status == IterationStatus.QUESTIONING && question == null) {
            throw new IllegalArgumentException("question must be non-null when status is QUESTIONING");
        }
        if (status != IterationStatus.QUESTIONING && question != null) {
            throw new IllegalArgumentException("question must be null unless status is QUESTIONING");
        }
    }

    public static PhaseExecutionResult success() {
        return success(0);
    }

    public static PhaseExecutionResult success(long tokenUsage) {
        return new PhaseExecutionResult(IterationStatus.SUCCESS, null, tokenUsage, null);
    }

    public static PhaseExecutionResult failed(String failureReason) {
        return new PhaseExecutionResult(IterationStatus.FAILED, failureReason, 0, null);
    }

    public static PhaseExecutionResult timedOut(String failureReason) {
        return new PhaseExecutionResult(IterationStatus.TIMED_OUT, failureReason, 0, null);
    }

    public static PhaseExecutionResult questioning(Question question, long tokenUsage) {
        return new PhaseExecutionResult(IterationStatus.QUESTIONING, null, tokenUsage, question);
    }
}
