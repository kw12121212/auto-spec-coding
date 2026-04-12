package org.specdriven.agent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ContextRetentionPolicyTest {

    private final ContextRetentionPolicy policy = new DefaultContextRetentionPolicy();

    @Test
    void recoveryContextIsMandatory() {
        ContextRetentionDecision decision = policy.evaluate(ContextRetentionCandidate.recovery("resume phase"));

        assertEquals(ContextRetentionLevel.MANDATORY, decision.level());
        assertTrue(decision.hasReason(ContextRetentionReason.RECOVERY_EXECUTION));
        assertTrue(decision.mandatory());
    }

    @Test
    void questionEscalationContextIsMandatory() {
        ContextRetentionDecision decision = policy.evaluate(
                ContextRetentionCandidate.questionEscalation("question-1"));

        assertEquals(ContextRetentionLevel.MANDATORY, decision.level());
        assertEquals(Set.of(ContextRetentionReason.QUESTION_ESCALATION), decision.reasons());
    }

    @Test
    void answerReplayContextIsMandatory() {
        ContextRetentionDecision decision = policy.evaluate(
                ContextRetentionCandidate.answerReplay("question-1"));

        assertEquals(ContextRetentionLevel.MANDATORY, decision.level());
        assertTrue(decision.hasReason(ContextRetentionReason.ANSWER_REPLAY));
    }

    @Test
    void auditTraceContextIsMandatory() {
        ContextRetentionDecision decision = policy.evaluate(
                ContextRetentionCandidate.auditTrace("session-1"));

        assertEquals(ContextRetentionLevel.MANDATORY, decision.level());
        assertTrue(decision.hasReason(ContextRetentionReason.AUDIT_TRACE));
    }

    @Test
    void activeToolCallContextIsMandatory() {
        ContextRetentionDecision decision = policy.evaluate(
                ContextRetentionCandidate.activeToolCall("tool-call-1"));

        assertEquals(ContextRetentionLevel.MANDATORY, decision.level());
        assertTrue(decision.hasReason(ContextRetentionReason.ACTIVE_TOOL_CALL));
    }

    @Test
    void multipleMandatoryReasonsArePreserved() {
        ContextRetentionCandidate candidate = new ContextRetentionCandidate(
                "resume with answer",
                "",
                "question-1",
                "",
                true,
                false,
                true,
                false,
                false,
                false);

        ContextRetentionDecision decision = policy.evaluate(candidate);

        assertEquals(ContextRetentionLevel.MANDATORY, decision.level());
        assertEquals(Set.of(
                ContextRetentionReason.RECOVERY_EXECUTION,
                ContextRetentionReason.ANSWER_REPLAY), decision.reasons());
    }

    @Test
    void ordinaryContextIsOptimizableButNotMandatory() {
        ContextRetentionDecision decision = policy.evaluate(
                ContextRetentionCandidate.ordinary("old build log"));

        assertEquals(ContextRetentionLevel.OPTIONAL, decision.level());
        assertFalse(decision.mandatory());
        assertTrue(decision.reasons().isEmpty());
    }

    @Test
    void emptyMetadataIsDiscardableWithNoReasons() {
        ContextRetentionDecision decision = policy.evaluate(ContextRetentionCandidate.empty());

        assertEquals(ContextRetentionLevel.DISCARDABLE, decision.level());
        assertTrue(decision.reasons().isEmpty());
    }

    @Test
    void relevanceAloneDoesNotMakeContextMandatory() {
        ContextRetentionDecision decision = policy.evaluate(
                ContextRetentionCandidate.relevant("grep deployment status"));

        assertEquals(ContextRetentionLevel.OPTIONAL, decision.level());
        assertFalse(decision.mandatory());
        assertTrue(decision.reasons().isEmpty());
    }

    @Test
    void lowRelevanceRecoveryContextRemainsMandatory() {
        ContextRetentionCandidate candidate = new ContextRetentionCandidate(
                "unrelated archived output",
                "",
                "",
                "",
                true,
                false,
                false,
                false,
                false,
                false);

        ContextRetentionDecision decision = policy.evaluate(candidate);

        assertEquals(ContextRetentionLevel.MANDATORY, decision.level());
        assertTrue(decision.hasReason(ContextRetentionReason.RECOVERY_EXECUTION));
    }

    @Test
    void nullCandidateIsRejectedWithDescriptiveMessage() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> policy.evaluate(null));

        assertEquals("context candidate", exception.getMessage());
    }

    @Test
    void identicalInputsProduceDeterministicDecisions() {
        ContextRetentionCandidate candidate = new ContextRetentionCandidate(
                "question and audit",
                "session-1",
                "question-1",
                "",
                false,
                true,
                false,
                true,
                false,
                false);

        ContextRetentionDecision first = policy.evaluate(candidate);
        ContextRetentionDecision second = policy.evaluate(candidate);
        ContextRetentionDecision third = policy.evaluate(candidate);

        assertEquals(first, second);
        assertEquals(second, third);
    }

    @Test
    void candidateNormalizesNullOptionalMetadata() {
        ContextRetentionCandidate candidate = new ContextRetentionCandidate(
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                false,
                false,
                false);

        assertEquals(ContextRetentionCandidate.empty(), candidate);
        assertEquals(ContextRetentionLevel.DISCARDABLE, policy.evaluate(candidate).level());
    }

    @Test
    void evaluatingPolicyDoesNotChangeExistingLlmRequest() {
        List<Message> messages = List.of(new UserMessage("hello", 1L));
        LlmRequest request = LlmRequest.of(messages, "system prompt");

        policy.evaluate(ContextRetentionCandidate.recovery("resume phase"));

        assertEquals(messages, request.messages());
        assertEquals("system prompt", request.systemPrompt());
        assertTrue(request.tools().isEmpty());
        assertEquals(0.7, request.temperature());
        assertEquals(4096, request.maxTokens());
        assertTrue(request.extra().isEmpty());
    }
}
