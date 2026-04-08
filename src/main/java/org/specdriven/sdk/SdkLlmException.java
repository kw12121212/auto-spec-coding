package org.specdriven.sdk;

/**
 * Exception thrown when LLM provider calls fail.
 * Defaults to retryable=true since LLM errors are often transient (timeouts, rate limits).
 */
public class SdkLlmException extends SdkException {

    public SdkLlmException(String message, Throwable cause) {
        super(message, cause, true);
    }

    public SdkLlmException(String message, Throwable cause, boolean retryable) {
        super(message, cause, retryable);
    }
}
