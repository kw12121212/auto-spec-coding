package org.specdriven.agent.loop;

import java.util.Collections;
import java.util.List;

import org.specdriven.agent.question.Question;

/**
 * Result of a single pipeline execution for one loop iteration.
 *
 * @param status          the outcome status
 * @param failureReason   reason for failure, null if successful
 * @param durationMs      wall-clock duration in milliseconds
 * @param phasesCompleted phases that completed successfully before termination
 * @param tokenUsage      total LLM tokens consumed
 * @param question        the Question that caused a QUESTIONING interruption; null for all other statuses
 */
public record IterationResult(
        IterationStatus status,
        String failureReason,
        long durationMs,
        List<PipelinePhase> phasesCompleted,
        long tokenUsage,
        Question question
) {
    public IterationResult {
        phasesCompleted = phasesCompleted == null
                ? List.of()
                : Collections.unmodifiableList(List.copyOf(phasesCompleted));
        if (durationMs < 0) throw new IllegalArgumentException("durationMs must be non-negative");
        if (tokenUsage < 0) throw new IllegalArgumentException("tokenUsage must be non-negative");
        if (status == IterationStatus.QUESTIONING && question == null) {
            throw new IllegalArgumentException("question must not be null when status is QUESTIONING");
        }
        if (status != IterationStatus.QUESTIONING && question != null) {
            throw new IllegalArgumentException("question must be null when status is not QUESTIONING");
        }
    }

    /**
     * Backward-compatible constructor without question (defaults to null).
     */
    public IterationResult(IterationStatus status, String failureReason,
                           long durationMs, List<PipelinePhase> phasesCompleted, long tokenUsage) {
        this(status, failureReason, durationMs, phasesCompleted, tokenUsage, null);
    }

    /**
     * Backward-compatible constructor without token usage or question (both default to 0/null).
     */
    public IterationResult(IterationStatus status, String failureReason,
                           long durationMs, List<PipelinePhase> phasesCompleted) {
        this(status, failureReason, durationMs, phasesCompleted, 0, null);
    }
}
