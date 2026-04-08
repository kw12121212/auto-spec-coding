package org.specdriven.agent.vault;

import org.specdriven.agent.event.EventBus;

/**
 * Convenience factory for creating {@link LealoneVault} instances with sensible defaults.
 */
public final class VaultFactory {

    private static final String DEFAULT_JDBC_URL = "jdbc:lealone:./vault";

    private VaultFactory() {}

    /**
     * Create a LealoneVault with the default JDBC URL and master key from {@link VaultMasterKey}.
     *
     * @param eventBus the event bus for vault event publishing (may be null)
     * @return a new LealoneVault instance
     */
    public static LealoneVault create(EventBus eventBus) {
        return create(eventBus, DEFAULT_JDBC_URL);
    }

    /**
     * Create a LealoneVault with a custom JDBC URL and master key from {@link VaultMasterKey}.
     *
     * @param eventBus the event bus for vault event publishing (may be null)
     * @param jdbcUrl  the JDBC URL for the Lealone database
     * @return a new LealoneVault instance
     */
    public static LealoneVault create(EventBus eventBus, String jdbcUrl) {
        return new LealoneVault(eventBus, jdbcUrl);
    }
}
