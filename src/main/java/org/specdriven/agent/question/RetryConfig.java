package org.specdriven.agent.question;

/**
 * Configuration for delivery retry behavior.
 *
 * @param maxAttempts       maximum number of send attempts (>= 1)
 * @param initialDelayMs    delay before the first retry in milliseconds (> 0)
 * @param backoffMultiplier multiplier applied to the delay after each retry (>= 1.0)
 */
public record RetryConfig(
        int maxAttempts,
        long initialDelayMs,
        double backoffMultiplier
) {
    public RetryConfig {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        if (initialDelayMs <= 0) {
            throw new IllegalArgumentException("initialDelayMs must be > 0");
        }
        if (backoffMultiplier < 1.0) {
            throw new IllegalArgumentException("backoffMultiplier must be >= 1.0");
        }
    }

    /** Creates a RetryConfig with defaults: maxAttempts=3, initialDelayMs=1000, backoffMultiplier=2.0. */
    public RetryConfig() {
        this(3, 1000, 2.0);
    }
}
