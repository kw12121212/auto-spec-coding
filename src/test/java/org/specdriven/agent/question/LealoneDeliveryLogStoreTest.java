package org.specdriven.agent.question;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class LealoneDeliveryLogStoreTest {

    private String jdbcUrl;
    private LealoneDeliveryLogStore store;

    @BeforeEach
    void setUp() {
        String dbName = "test_delivery_log_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        jdbcUrl = "jdbc:lealone:embed:" + dbName + "?PERSISTENT=false";
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
    void save_roundTripsAllFieldsThroughMappedStorage() {
        DeliveryAttempt attempt = new DeliveryAttempt(
                "q-round-trip",
                "telegram",
                4,
                DeliveryStatus.RETRYING,
                429,
                "rate limited",
                123456789L
        );

        store.save(attempt);

        List<DeliveryAttempt> results = store.findByQuestion("q-round-trip");
        assertEquals(List.of(attempt), results);
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
        store.save(new DeliveryAttempt("q-3", "telegram", 2, DeliveryStatus.RETRYING, 503, null, 1001L));

        List<DeliveryAttempt> results = store.findByQuestion("q-3");
        assertEquals(2, results.size());
        assertNull(results.get(0).statusCode());
        assertEquals("timeout", results.get(0).errorMessage());
        assertEquals(503, results.get(1).statusCode());
        assertNull(results.get(1).errorMessage());
    }

    @Test
    void readsRowsCompatibleWithExistingDeliveryLogTable() throws Exception {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "root", "");
             PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO delivery_log
                         (question_id, channel_type, attempt_number, status, status_code, error_message, attempted_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?)
                     """)) {
            ps.setString(1, "q-existing-row");
            ps.setString(2, "discord");
            ps.setInt(3, 7);
            ps.setString(4, DeliveryStatus.SENT.name());
            ps.setInt(5, 201);
            ps.setString(6, "accepted");
            ps.setLong(7, 2222L);
            ps.executeUpdate();
        }

        List<DeliveryAttempt> results = store.findByQuestion("q-existing-row");
        assertEquals(List.of(new DeliveryAttempt(
                "q-existing-row",
                "discord",
                7,
                DeliveryStatus.SENT,
                201,
                "accepted",
                2222L
        )), results);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static DeliveryAttempt attempt(String questionId, String channelType, int attemptNumber, DeliveryStatus status) {
        return new DeliveryAttempt(questionId, channelType, attemptNumber, status, null, null, System.currentTimeMillis());
    }
}
