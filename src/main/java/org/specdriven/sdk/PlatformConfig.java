package org.specdriven.sdk;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Typed holder for platform-level Lealone parameters.
 * Use {@link #defaults()} for the standard embedded configuration.
 */
public record PlatformConfig(String jdbcUrl, Path compileCachePath) {

    public PlatformConfig {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl must not be null");
        if (jdbcUrl.isBlank()) throw new IllegalArgumentException("jdbcUrl must not be blank");
        Objects.requireNonNull(compileCachePath, "compileCachePath must not be null");
    }

    /**
     * Returns the default platform configuration matching the embedded Lealone setup.
     */
    public static PlatformConfig defaults() {
        return new PlatformConfig(
                "jdbc:lealone:embed:agent_db",
                Path.of(System.getProperty("java.io.tmpdir"), "specdriven-skill-cache")
        );
    }
}
