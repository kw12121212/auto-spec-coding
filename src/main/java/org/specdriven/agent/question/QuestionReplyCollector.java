package org.specdriven.agent.question;

/**
 * Receives and validates external human replies to waiting questions.
 */
public interface QuestionReplyCollector extends AutoCloseable {

    /**
     * Collects an answer for a waiting question.
     *
     * @param sessionId  the session that owns the question
     * @param questionId the question being answered
     * @param answer     the answer from an external source
     * @throws IllegalArgumentException if the answer does not match the waiting question
     * @throws IllegalStateException    if no question is waiting for the given session
     */
    void collect(String sessionId, String questionId, Answer answer);

    /**
     * Releases any resources held by this collector.
     */
    @Override
    void close();
}
