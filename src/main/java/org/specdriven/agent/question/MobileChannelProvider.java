package org.specdriven.agent.question;

/**
 * Factory SPI for creating mobile channel handles from configuration.
 * Implementations are registered by name in {@link MobileChannelRegistry}.
 */
@FunctionalInterface
public interface MobileChannelProvider {
    MobileChannelHandle create(MobileChannelConfig config);
}
