package org.specdriven.agent.vault;

import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Provides the master encryption key for the vault.
 * Reads from the {@code SPEC_DRIVEN_MASTER_KEY} environment variable.
 * Falls back to a fixed default key for development when the env var is not set,
 * logging a warning that the default must not be used in production.
 */
public final class VaultMasterKey {

    private static final Logger LOG = Logger.getLogger(VaultMasterKey.class.getName());

    static final String ENV_VAR = "SPEC_DRIVEN_MASTER_KEY";

    static final String DEFAULT_KEY = "spec-driven-dev-default-master-key-do-not-use-in-prod";

    private static volatile String cachedKey;
    private static volatile Boolean cachedIsDefault;
    private static volatile Supplier<String> envSource = () -> System.getenv(ENV_VAR);

    private VaultMasterKey() {}

    /**
     * Get the master key. Reads from the environment variable on first call,
     * then caches the result.
     *
     * @return the master key string
     */
    public static String get() {
        if (cachedKey == null) {
            synchronized (VaultMasterKey.class) {
                if (cachedKey == null) {
                    String envKey = envSource.get();
                    if (envKey != null && !envKey.isEmpty()) {
                        cachedKey = envKey;
                        cachedIsDefault = false;
                    } else {
                        cachedKey = DEFAULT_KEY;
                        cachedIsDefault = true;
                        LOG.warning("Using default vault master key — set " + ENV_VAR
                                + " environment variable for production use");
                    }
                }
            }
        }
        return cachedKey;
    }

    /**
     * Check whether the default development key is in use.
     *
     * @return true if using the default key, false if using a custom key from the env var
     */
    public static boolean isDefault() {
        if (cachedIsDefault == null) {
            get();
        }
        return cachedIsDefault;
    }

    /**
     * Reset cached state and restore default env source (for testing).
     */
    static synchronized void reset() {
        cachedKey = null;
        cachedIsDefault = null;
        envSource = () -> System.getenv(ENV_VAR);
    }

    /**
     * Set the environment variable source and reset cached state (for testing).
     */
    static synchronized void setEnvSource(Supplier<String> source) {
        envSource = source;
        cachedKey = null;
        cachedIsDefault = null;
    }
}
