package org.specdriven.agent.question;

/**
 * Pushes question notifications to an external channel.
 */
public interface QuestionDeliveryChannel extends AutoCloseable {

    /**
     * Sends a question to this channel for external consumption.
     *
     * @param question the question to deliver (never null)
     */
    void send(Question question);

    /**
     * Releases any resources held by this channel.
     */
    @Override
    void close();
}
