package org.specdriven.sdk;

/**
 * Unified exception type for all SDK operations.
 * Wraps internal exceptions (ConfigException, VaultException, etc.) with a descriptive message.
 */
public class SdkException extends RuntimeException {

    private final boolean retryable;

    public SdkException(String message) {
        super(message);
        this.retryable = false;
    }

    public SdkException(String message, Throwable cause) {
        super(message, cause);
        this.retryable = false;
    }

    public SdkException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
