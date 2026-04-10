package org.specdriven.agent.question;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RetryConfigTest {

    @Test
    void defaults() {
        RetryConfig config = new RetryConfig();
        assertEquals(3, config.maxAttempts());
        assertEquals(1000, config.initialDelayMs());
        assertEquals(2.0, config.backoffMultiplier());
    }

    @Test
    void customValues() {
        RetryConfig config = new RetryConfig(5, 500, 1.5);
        assertEquals(5, config.maxAttempts());
        assertEquals(500, config.initialDelayMs());
        assertEquals(1.5, config.backoffMultiplier());
    }

    @Test
    void rejectsMaxAttemptsLessThanOne() {
        assertThrows(IllegalArgumentException.class, () -> new RetryConfig(0, 1000, 2.0));
    }

    @Test
    void rejectsNegativeMaxAttempts() {
        assertThrows(IllegalArgumentException.class, () -> new RetryConfig(-1, 1000, 2.0));
    }

    @Test
    void rejectsZeroInitialDelay() {
        assertThrows(IllegalArgumentException.class, () -> new RetryConfig(3, 0, 2.0));
    }

    @Test
    void rejectsNegativeInitialDelay() {
        assertThrows(IllegalArgumentException.class, () -> new RetryConfig(3, -100, 2.0));
    }

    @Test
    void rejectsBackoffMultiplierLessThanOne() {
        assertThrows(IllegalArgumentException.class, () -> new RetryConfig(3, 1000, 0.5));
    }

    @Test
    void acceptsBackoffMultiplierOfOne() {
        assertDoesNotThrow(() -> new RetryConfig(3, 1000, 1.0));
    }
}
