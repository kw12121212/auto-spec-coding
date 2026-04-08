package org.specdriven.sdk;

/**
 * Exception thrown when tool execution fails.
 */
public class SdkToolException extends SdkException {

    public SdkToolException(String message, Throwable cause) {
        super(message, cause, false);
    }

    public SdkToolException(String message, Throwable cause, boolean retryable) {
        super(message, cause, retryable);
    }
}
