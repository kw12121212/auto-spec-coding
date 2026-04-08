package org.specdriven.sdk;

/**
 * Exception thrown when a permission check denies an operation.
 */
public class SdkPermissionException extends SdkException {

    public SdkPermissionException(String message, Throwable cause) {
        super(message, cause, false);
    }

    public SdkPermissionException(String message, Throwable cause, boolean retryable) {
        super(message, cause, retryable);
    }
}
