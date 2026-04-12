package org.specdriven.agent.agent;

/**
 * Observable reason a context candidate must be preserved.
 */
public enum ContextRetentionReason {
    RECOVERY_EXECUTION,
    QUESTION_ESCALATION,
    ANSWER_REPLAY,
    AUDIT_TRACE,
    ACTIVE_TOOL_CALL
}
