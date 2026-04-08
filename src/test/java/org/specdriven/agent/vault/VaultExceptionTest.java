package org.specdriven.agent.vault;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VaultExceptionTest {

    @Test
    void messageOnlyConstructor() {
        VaultException ex = new VaultException("key not found");

        assertEquals("key not found", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void messageWithCauseConstructor() {
        RuntimeException cause = new RuntimeException("root cause");
        VaultException ex = new VaultException("decryption failed", cause);

        assertEquals("decryption failed", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void isRuntimeException() {
        VaultException ex = new VaultException("test");
        assertTrue(ex instanceof RuntimeException);
    }
}
