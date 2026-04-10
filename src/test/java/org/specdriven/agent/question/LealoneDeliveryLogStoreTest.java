package org.specdriven.agent.question;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class LealoneDeliveryLogStoreTest {

    private LealoneDeliveryLogStore store;

    @BeforeEach
    void setUp() {
        String dbName = "test_delivery_log_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String jdbcUrl = "jdbc:lealone:embed:" + dbName + "?PERSISTENT=false";
        store = new LealoneDeliveryLogStore(jdbcUrl);
    }

    // -------------------------------------------------------------------------
    // save / findByQuestion
    // -------------------------------------------------------------------------

    @Test
    void save_andFindByQuestion() {
        store.save(attempt("q-1", "telegram", 1, DeliveryStatus.SENT));
        store.save(attempt("q-1", "telegram", 2, DeliveryStatus.SENT));

        List<DeliveryAttempt> results = store.findByQuestion("q-1");
        assertEquals(2, results.size());
        assertEquals(1, results.get(0).attemptNumber());
        assertEquals(2, results.get(1).attemptNumber());
    }

    @Test
    void findByQuestion_returnsEmptyForUnknown() {
        assertTrue(store.findByQuestion("unknown").isEmpty());
    }

    // -------------------------------------------------------------------------
    // findLatestByQuestion
    // -------------------------------------------------------------------------

    @Test
    void findLatestByQuestion_returnsHighestAttempt() {
        store.save(attempt("q-2", "discord", 1, DeliveryStatus.RETRYING));
        store.save(attempt("q-2", "discord", 2, DeliveryStatus.RETRYING));
        store.save(attempt("q-2", "discord", 3, DeliveryStatus.SENT));

        Optional<DeliveryAttempt> latest = store.findLatestByQuestion("q-2");
        assertTrue(latest.isPresent());
        assertEquals(3, latest.get().attemptNumber());
        assertEquals(DeliveryStatus.SENT, latest.get().status());
    }

    @Test
    void findLatestByQuestion_returnsEmptyForUnknown() {
        assertTrue(store.findLatestByQuestion("unknown").isEmpty());
    }

    // -------------------------------------------------------------------------
    // Nullable fields
    // -------------------------------------------------------------------------

    @Test
    void savesNullableFields() {
        store.save(new DeliveryAttempt("q-3", "telegram", 1, DeliveryStatus.FAILED, null, "timeout", 1000L));

        List<DeliveryAttempt> results = store.findByQuestion("q-3");
        assertEquals(1, results.size());
        assertNull(results.get(0).statusCode());
        assertEquals("timeout", results.get(0).errorMessage());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static DeliveryAttempt attempt(String questionId, String channelType, int attemptNumber, DeliveryStatus status) {
        return new DeliveryAttempt(questionId, channelType, attemptNumber, status, null, null, System.currentTimeMillis());
    }
}
