package org.specdriven.agent.vault;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.testsupport.CapturingEventBus;
import org.specdriven.agent.testsupport.LealoneTestDb;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("VaultMasterKey")
class LealoneVaultTest {

    private String jdbcUrl;
    private CapturingEventBus eventBus;

    @BeforeEach
    void setUp() {
        jdbcUrl = LealoneTestDb.freshJdbcUrl();
        eventBus = new CapturingEventBus();
        VaultMasterKey.setEnvSource(() -> "test-master-key");
    }

    @AfterEach
    void tearDown() {
        VaultMasterKey.reset();
    }

    @Test
    void getReturnsDecryptedValueAfterSet() {
        LealoneVault vault = new LealoneVault(eventBus, jdbcUrl);
        vault.set("openai_key", "sk-abc123", "OpenAI API key");
        assertEquals("sk-abc123", vault.get("openai_key"));
    }

    @Test
    void getThrowsForMissingKey() {
        LealoneVault vault = new LealoneVault(eventBus, jdbcUrl);
        assertThrows(VaultException.class, () -> vault.get("nonexistent"));
    }

    @Test
    void setOverwritesExistingKey() {
        LealoneVault vault = new LealoneVault(eventBus, jdbcUrl);
        vault.set("api_key", "old_value", "first");
        vault.set("api_key", "new_value", "rotated");
        assertEquals("new_value", vault.get("api_key"));
    }

    @Test
    void deleteRemovesKey() {
        LealoneVault vault = new LealoneVault(eventBus, jdbcUrl);
        vault.set("temp_key", "secret", "temp");
        vault.delete("temp_key");
        assertFalse(vault.exists("temp_key"));
        assertThrows(VaultException.class, () -> vault.get("temp_key"));
    }

    @Test
    void deleteIsIdempotentForMissingKey() {
        LealoneVault vault = new LealoneVault(eventBus, jdbcUrl);
        assertDoesNotThrow(() -> vault.delete("ghost"));
    }

    @Test
    void listReturnsEntriesWithoutDecryptedValues() {
        LealoneVault vault = new LealoneVault(eventBus, jdbcUrl);
        vault.set("key_a", "value_a", "desc a");
        vault.set("key_b", "value_b", "desc b");

        List<VaultEntry> entries = vault.list();
        assertEquals(2, entries.size());

        for (VaultEntry entry : entries) {
            assertNotNull(entry.key());
            assertNotNull(entry.createdAt());
            assertNotNull(entry.description());
        }

        assertTrue(entries.stream().anyMatch(e -> "key_a".equals(e.key())));
        assertTrue(entries.stream().anyMatch(e -> "key_b".equals(e.key())));
    }

    @Test
    void existsReturnsCorrectly() {
        LealoneVault vault = new LealoneVault(eventBus, jdbcUrl);
        assertFalse(vault.exists("no_such_key"));
        vault.set("exists_key", "val", "test");
        assertTrue(vault.exists("exists_key"));
    }

    @Test
    void storedValueIsNotPlaintext() throws Exception {
        LealoneVault vault = new LealoneVault(eventBus, jdbcUrl);
        vault.set("api_key", "secret_val", "test");

        try (Connection conn = DriverManager.getConnection(jdbcUrl, "root", "");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT encrypted_value FROM vault_secrets WHERE key = 'api_key'")) {
            assertTrue(rs.next());
            String stored = rs.getString("encrypted_value");
            assertNotEquals("secret_val", stored);
        }
    }

    @Test
    void wrongMasterKeyFailsToDecrypt() {
        LealoneVault vaultA = new LealoneVault(eventBus, jdbcUrl);
        vaultA.set("test", "value", "desc");

        VaultMasterKey.setEnvSource(() -> "different-master-key");
        LealoneVault vaultB = new LealoneVault(eventBus, jdbcUrl);

        assertThrows(VaultException.class, () -> vaultB.get("test"));
    }

    @Test
    void auditLogCreatedOnSet() throws Exception {
        LealoneVault vault = new LealoneVault(eventBus, jdbcUrl);
        vault.set("key1", "val", "desc");

        try (Connection conn = DriverManager.getConnection(jdbcUrl, "root", "");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM vault_audit_log WHERE vault_key = 'key1'")) {
            assertTrue(rs.next());
            assertEquals("SET", rs.getString("operation"));
        }
    }

    @Test
    void auditLogCreatedOnDelete() throws Exception {
        LealoneVault vault = new LealoneVault(eventBus, jdbcUrl);
        vault.set("key1", "val", "desc");
        vault.delete("key1");

        try (Connection conn = DriverManager.getConnection(jdbcUrl, "root", "");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM vault_audit_log WHERE vault_key = 'key1' AND operation = 'DELETE'")) {
            assertTrue(rs.next());
        }
    }

    @Test
    void getDoesNotCreateAuditLog() throws Exception {
        LealoneVault vault = new LealoneVault(eventBus, jdbcUrl);
        vault.set("key1", "val", "desc");
        vault.get("key1");

        try (Connection conn = DriverManager.getConnection(jdbcUrl, "root", "");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM vault_audit_log WHERE vault_key = 'key1'")) {
            assertTrue(rs.next());
            // Only the SET entry, no GET entry
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    void setPublishesEvent() {
        LealoneVault vault = new LealoneVault(eventBus, jdbcUrl);
        vault.set("key1", "val", "desc");

        assertFalse(eventBus.getEvents().isEmpty());
        Event event = eventBus.getEvents().get(eventBus.getEvents().size() - 1);
        assertEquals(EventType.VAULT_SECRET_CREATED, event.type());
        assertEquals("key1", event.metadata().get("vaultKey"));
    }

    @Test
    void deletePublishesEvent() {
        LealoneVault vault = new LealoneVault(eventBus, jdbcUrl);
        vault.set("key1", "val", "desc");
        eventBus.clear();

        vault.delete("key1");

        assertFalse(eventBus.getEvents().isEmpty());
        Event event = eventBus.getEvents().get(eventBus.getEvents().size() - 1);
        assertEquals(EventType.VAULT_SECRET_DELETED, event.type());
        assertEquals("key1", event.metadata().get("vaultKey"));
    }

    @Test
    void tableCreationIsIdempotent() {
        LealoneVault vault1 = new LealoneVault(eventBus, jdbcUrl);
        vault1.set("key1", "val", "desc");

        assertDoesNotThrow(() -> {
            LealoneVault vault2 = new LealoneVault(eventBus, jdbcUrl);
            assertEquals("val", vault2.get("key1"));
        });
    }

}
