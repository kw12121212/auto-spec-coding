package org.specdriven.agent.question;

/**
 * Final handling outcome for a question.
 */
public enum QuestionDecision {
    ANSWER_ACCEPTED,
    ESCALATE_TO_HUMAN,
    TIMEOUT,
    CANCELLED
}
