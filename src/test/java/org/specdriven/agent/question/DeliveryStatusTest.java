package org.specdriven.agent.question;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeliveryStatusTest {

    @Test
    void containsAllRequiredValues() {
        assertNotNull(DeliveryStatus.valueOf("PENDING"));
        assertNotNull(DeliveryStatus.valueOf("SENT"));
        assertNotNull(DeliveryStatus.valueOf("FAILED"));
        assertNotNull(DeliveryStatus.valueOf("RETRYING"));
    }

    @Test
    void hasExactlyFourValues() {
        assertEquals(4, DeliveryStatus.values().length);
    }
}
