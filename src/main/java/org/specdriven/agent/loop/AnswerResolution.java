package org.specdriven.agent.loop;

import org.specdriven.agent.question.Answer;

import java.util.Objects;

/**
 * Sealed result of a {@link LoopAnswerAgent#resolve} call.
 */
public sealed interface AnswerResolution permits AnswerResolution.Resolved, AnswerResolution.Escalated {

    /**
     * The LoopAnswerAgent successfully submitted an answer.
     *
     * @param answer the answer that was accepted; must not be null
     */
    record Resolved(Answer answer) implements AnswerResolution {
        public Resolved {
            Objects.requireNonNull(answer, "answer");
        }
    }

    /**
     * The question could not be answered automatically and requires escalation.
     *
     * @param reason human-readable reason for escalation; must not be null
     */
    record Escalated(String reason) implements AnswerResolution {
        public Escalated {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
