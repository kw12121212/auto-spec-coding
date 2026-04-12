package org.specdriven.agent.agent;

/**
 * Explicit metadata used by a retention policy to classify one context item.
 */
public record ContextRetentionCandidate(
        String content,
        String sessionId,
        String questionId,
        String toolCallId,
        boolean recoveryExecutionRequired,
        boolean questionEscalationRequired,
        boolean answerReplayRequired,
        boolean auditTraceRequired,
        boolean activeToolCall,
        boolean relevantToCurrentTurn) {

    public ContextRetentionCandidate {
        content = content == null ? "" : content;
        sessionId = sessionId == null ? "" : sessionId;
        questionId = questionId == null ? "" : questionId;
        toolCallId = toolCallId == null ? "" : toolCallId;
    }

    public static ContextRetentionCandidate empty() {
        return new ContextRetentionCandidate("", "", "", "", false, false, false, false, false, false);
    }

    public static ContextRetentionCandidate ordinary(String content) {
        return new ContextRetentionCandidate(content, "", "", "", false, false, false, false, false, false);
    }

    public static ContextRetentionCandidate recovery(String content) {
        return new ContextRetentionCandidate(content, "", "", "", true, false, false, false, false, false);
    }

    public static ContextRetentionCandidate questionEscalation(String questionId) {
        return new ContextRetentionCandidate("", "", questionId, "", false, true, false, false, false, false);
    }

    public static ContextRetentionCandidate answerReplay(String questionId) {
        return new ContextRetentionCandidate("", "", questionId, "", false, false, true, false, false, false);
    }

    public static ContextRetentionCandidate auditTrace(String sessionId) {
        return new ContextRetentionCandidate("", sessionId, "", "", false, false, false, true, false, false);
    }

    public static ContextRetentionCandidate activeToolCall(String toolCallId) {
        return new ContextRetentionCandidate("", "", "", toolCallId, false, false, false, false, true, false);
    }

    public static ContextRetentionCandidate relevant(String content) {
        return new ContextRetentionCandidate(content, "", "", "", false, false, false, false, false, true);
    }
}
