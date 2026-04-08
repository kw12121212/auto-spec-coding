package org.specdriven.sdk;

/**
 * Exception thrown when SDK configuration loading or parsing fails.
 */
public class SdkConfigException extends SdkException {

    public SdkConfigException(String message, Throwable cause) {
        super(message, cause, false);
    }

    public SdkConfigException(String message, Throwable cause, boolean retryable) {
        super(message, cause, retryable);
    }
}
