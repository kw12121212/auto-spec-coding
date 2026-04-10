package org.specdriven.agent.question;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeliveryAttemptTest {

    @Test
    void createsWithAllFields() {
        DeliveryAttempt attempt = new DeliveryAttempt(
                "q-1", "telegram", 1, DeliveryStatus.SENT, 200, null, 1000L);
        assertEquals("q-1", attempt.questionId());
        assertEquals("telegram", attempt.channelType());
        assertEquals(1, attempt.attemptNumber());
        assertEquals(DeliveryStatus.SENT, attempt.status());
        assertEquals(200, attempt.statusCode());
        assertNull(attempt.errorMessage());
        assertEquals(1000L, attempt.attemptedAt());
    }

    @Test
    void createsWithNullableFields() {
        DeliveryAttempt attempt = new DeliveryAttempt(
                "q-2", "discord", 2, DeliveryStatus.FAILED, null, "timeout", 2000L);
        assertNull(attempt.statusCode());
        assertEquals("timeout", attempt.errorMessage());
    }

    @Test
    void rejectsBlankQuestionId() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeliveryAttempt("", "telegram", 1, DeliveryStatus.SENT, null, null, 1000L));
    }

    @Test
    void rejectsNullQuestionId() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeliveryAttempt(null, "telegram", 1, DeliveryStatus.SENT, null, null, 1000L));
    }

    @Test
    void rejectsBlankChannelType() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeliveryAttempt("q-1", "", 1, DeliveryStatus.SENT, null, null, 1000L));
    }

    @Test
    void rejectsZeroAttemptNumber() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeliveryAttempt("q-1", "telegram", 0, DeliveryStatus.SENT, null, null, 1000L));
    }

    @Test
    void rejectsNullStatus() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeliveryAttempt("q-1", "telegram", 1, null, null, null, 1000L));
    }
}
