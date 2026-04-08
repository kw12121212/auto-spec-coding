package org.specdriven.sdk;

/**
 * Exception thrown when secret vault operations fail.
 */
public class SdkVaultException extends SdkException {

    public SdkVaultException(String message, Throwable cause) {
        super(message, cause, false);
    }

    public SdkVaultException(String message, Throwable cause, boolean retryable) {
        super(message, cause, retryable);
    }
}
