package org.specdriven.agent.question;

/**
 * Status of a single delivery attempt to an external channel.
 */
public enum DeliveryStatus {
    PENDING,
    SENT,
    FAILED,
    RETRYING
}
