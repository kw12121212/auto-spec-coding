package org.specdriven.sdk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SdkExceptionTest {

    @Test
    void messageOnlyConstructor() {
        SdkException ex = new SdkException("something went wrong");
        assertEquals("something went wrong", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void messageAndCauseConstructor() {
        RuntimeException cause = new RuntimeException("root cause");
        SdkException ex = new SdkException("wrapper message", cause);
        assertEquals("wrapper message", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void causeIsPreservedThroughChain() {
        Exception root = new IllegalArgumentException("bad arg");
        Exception mid = new RuntimeException("mid", root);
        SdkException ex = new SdkException("sdk error", mid);
        assertSame(mid, ex.getCause());
        assertSame(root, ex.getCause().getCause());
    }

    @Test
    void isRuntimeException() {
        SdkException ex = new SdkException("test");
        assertTrue(ex instanceof RuntimeException);
    }

    @Test
    void defaultNotRetryable() {
        SdkException ex = new SdkException("error");
        assertFalse(ex.isRetryable());
    }

    @Test
    void defaultNotRetryableWithCause() {
        SdkException ex = new SdkException("error", new RuntimeException("cause"));
        assertFalse(ex.isRetryable());
    }

    @Test
    void explicitRetryableTrue() {
        SdkException ex = new SdkException("error", new RuntimeException("cause"), true);
        assertTrue(ex.isRetryable());
    }

    @Test
    void explicitRetryableFalse() {
        SdkException ex = new SdkException("error", new RuntimeException("cause"), false);
        assertFalse(ex.isRetryable());
    }
}
