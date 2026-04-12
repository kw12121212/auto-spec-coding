package org.specdriven.agent.agent;

import java.util.EnumSet;
import java.util.Objects;

/**
 * Rule-based retention policy for context that must not be trimmed.
 */
public final class DefaultContextRetentionPolicy implements ContextRetentionPolicy {

    @Override
    public ContextRetentionDecision evaluate(ContextRetentionCandidate candidate) {
        Objects.requireNonNull(candidate, "context candidate");

        EnumSet<ContextRetentionReason> reasons = EnumSet.noneOf(ContextRetentionReason.class);
        if (candidate.recoveryExecutionRequired()) {
            reasons.add(ContextRetentionReason.RECOVERY_EXECUTION);
        }
        if (candidate.questionEscalationRequired()) {
            reasons.add(ContextRetentionReason.QUESTION_ESCALATION);
        }
        if (candidate.answerReplayRequired()) {
            reasons.add(ContextRetentionReason.ANSWER_REPLAY);
        }
        if (candidate.auditTraceRequired()) {
            reasons.add(ContextRetentionReason.AUDIT_TRACE);
        }
        if (candidate.activeToolCall()) {
            reasons.add(ContextRetentionReason.ACTIVE_TOOL_CALL);
        }

        if (!reasons.isEmpty()) {
            return new ContextRetentionDecision(ContextRetentionLevel.MANDATORY, reasons);
        }
        if (hasOptimizableMetadata(candidate)) {
            return new ContextRetentionDecision(ContextRetentionLevel.OPTIONAL, reasons);
        }
        return new ContextRetentionDecision(ContextRetentionLevel.DISCARDABLE, reasons);
    }

    private static boolean hasOptimizableMetadata(ContextRetentionCandidate candidate) {
        return !candidate.content().isBlank()
                || !candidate.sessionId().isBlank()
                || !candidate.questionId().isBlank()
                || !candidate.toolCallId().isBlank()
                || candidate.relevantToCurrentTurn();
    }
}
