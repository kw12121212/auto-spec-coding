package org.specdriven.sdk;

/**
 * Unified exception type for all SDK operations.
 * Wraps internal exceptions (ConfigException, VaultException, etc.) with a descriptive message.
 */
public class SdkException extends RuntimeException {

    public SdkException(String message) {
        super(message);
    }

    public SdkException(String message, Throwable cause) {
        super(message, cause);
    }
}
