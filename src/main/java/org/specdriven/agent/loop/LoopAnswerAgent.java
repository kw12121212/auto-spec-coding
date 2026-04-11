package org.specdriven.agent.loop;

import org.specdriven.agent.question.Question;

/**
 * Resolves a {@link Question} automatically within the autonomous loop.
 *
 * <p>Implementations must not throw checked exceptions — all failures are
 * returned as {@link AnswerResolution.Escalated}.
 * When {@code timeoutSeconds} is exceeded the implementation MUST return
 * {@code Escalated("timeout")}.
 * Implementations MUST NOT modify the supplied {@link Question} object.
 */
public interface LoopAnswerAgent {

    /**
     * Attempts to resolve the given question within the specified timeout.
     *
     * @param question       the question to resolve; never null
     * @param timeoutSeconds maximum seconds to spend resolving; positive
     * @return a {@link AnswerResolution.Resolved} or {@link AnswerResolution.Escalated}; never null
     */
    AnswerResolution resolve(Question question, int timeoutSeconds);
}
