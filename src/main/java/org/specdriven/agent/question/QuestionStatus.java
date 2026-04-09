package org.specdriven.agent.question;

/**
 * Observable lifecycle states for a structured question.
 */
public enum QuestionStatus {
    OPEN,
    WAITING_FOR_ANSWER,
    ANSWERED,
    ESCALATED,
    EXPIRED,
    CLOSED
}
