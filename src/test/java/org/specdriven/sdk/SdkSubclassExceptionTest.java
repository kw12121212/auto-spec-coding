package org.specdriven.sdk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SdkSubclassExceptionTest {

    // --- SdkConfigException ---

    @Test
    void sdkConfigException_defaultNotRetryable() {
        SdkConfigException ex = new SdkConfigException("bad config", new RuntimeException("cause"));
        assertFalse(ex.isRetryable());
    }

    @Test
    void sdkConfigException_explicitRetryable() {
        SdkConfigException ex = new SdkConfigException("bad config", new RuntimeException("cause"), true);
        assertTrue(ex.isRetryable());
    }

    @Test
    void sdkConfigException_messageAndCause() {
        RuntimeException cause = new RuntimeException("root");
        SdkConfigException ex = new SdkConfigException("config failed", cause);
        assertEquals("config failed", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void sdkConfigException_isSdkException() {
        SdkConfigException ex = new SdkConfigException("err", new RuntimeException());
        assertTrue(ex instanceof SdkException);
    }

    // --- SdkVaultException ---

    @Test
    void sdkVaultException_defaultNotRetryable() {
        SdkVaultException ex = new SdkVaultException("vault error", new RuntimeException("cause"));
        assertFalse(ex.isRetryable());
    }

    @Test
    void sdkVaultException_explicitRetryable() {
        SdkVaultException ex = new SdkVaultException("vault error", new RuntimeException("cause"), true);
        assertTrue(ex.isRetryable());
    }

    @Test
    void sdkVaultException_messageAndCause() {
        RuntimeException cause = new RuntimeException("root");
        SdkVaultException ex = new SdkVaultException("vault failed", cause);
        assertEquals("vault failed", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void sdkVaultException_isSdkException() {
        SdkVaultException ex = new SdkVaultException("err", new RuntimeException());
        assertTrue(ex instanceof SdkException);
    }

    // --- SdkLlmException ---

    @Test
    void sdkLlmException_defaultRetryable() {
        SdkLlmException ex = new SdkLlmException("timeout", new RuntimeException("cause"));
        assertTrue(ex.isRetryable());
    }

    @Test
    void sdkLlmException_explicitNotRetryable() {
        SdkLlmException ex = new SdkLlmException("auth error", new RuntimeException("cause"), false);
        assertFalse(ex.isRetryable());
    }

    @Test
    void sdkLlmException_messageAndCause() {
        RuntimeException cause = new RuntimeException("root");
        SdkLlmException ex = new SdkLlmException("llm failed", cause);
        assertEquals("llm failed", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void sdkLlmException_isSdkException() {
        SdkLlmException ex = new SdkLlmException("err", new RuntimeException());
        assertTrue(ex instanceof SdkException);
    }

    // --- SdkToolException ---

    @Test
    void sdkToolException_defaultNotRetryable() {
        SdkToolException ex = new SdkToolException("tool error", new RuntimeException("cause"));
        assertFalse(ex.isRetryable());
    }

    @Test
    void sdkToolException_explicitRetryable() {
        SdkToolException ex = new SdkToolException("tool error", new RuntimeException("cause"), true);
        assertTrue(ex.isRetryable());
    }

    @Test
    void sdkToolException_messageAndCause() {
        RuntimeException cause = new RuntimeException("root");
        SdkToolException ex = new SdkToolException("tool failed", cause);
        assertEquals("tool failed", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void sdkToolException_isSdkException() {
        SdkToolException ex = new SdkToolException("err", new RuntimeException());
        assertTrue(ex instanceof SdkException);
    }

    // --- SdkPermissionException ---

    @Test
    void sdkPermissionException_defaultNotRetryable() {
        SdkPermissionException ex = new SdkPermissionException("denied", new RuntimeException("cause"));
        assertFalse(ex.isRetryable());
    }

    @Test
    void sdkPermissionException_explicitRetryable() {
        SdkPermissionException ex = new SdkPermissionException("denied", new RuntimeException("cause"), true);
        assertTrue(ex.isRetryable());
    }

    @Test
    void sdkPermissionException_messageAndCause() {
        RuntimeException cause = new RuntimeException("root");
        SdkPermissionException ex = new SdkPermissionException("permission denied", cause);
        assertEquals("permission denied", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void sdkPermissionException_isSdkException() {
        SdkPermissionException ex = new SdkPermissionException("err", new RuntimeException());
        assertTrue(ex instanceof SdkException);
    }

    // --- Catch by specific type ---

    @Test
    void catchLlmExceptionByType() {
        RuntimeException thrown = catchException(() -> {
            throw new SdkLlmException("llm error", new RuntimeException());
        });
        assertInstanceOf(SdkLlmException.class, thrown);

        // Also caught as SdkException
        assertInstanceOf(SdkException.class, thrown);
    }

    @Test
    void catchToolExceptionByType() {
        RuntimeException thrown = catchException(() -> {
            throw new SdkToolException("tool error", new RuntimeException());
        });
        assertInstanceOf(SdkToolException.class, thrown);
        assertInstanceOf(SdkException.class, thrown);
    }

    private RuntimeException catchException(Runnable action) {
        try {
            action.run();
            fail("Expected exception");
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }
}
