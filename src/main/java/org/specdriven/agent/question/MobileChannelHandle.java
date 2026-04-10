package org.specdriven.agent.question;

/**
 * A matched pair of delivery channel and reply collector produced by a {@link MobileChannelProvider}.
 * Mobile channels often need a coordinated sender/receiver pair
 * (e.g., a webhook sender and a callback receiver).
 */
public record MobileChannelHandle(
        QuestionDeliveryChannel channel,
        QuestionReplyCollector collector
) {}
