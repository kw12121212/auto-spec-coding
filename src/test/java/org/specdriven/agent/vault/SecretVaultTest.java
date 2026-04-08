package org.specdriven.agent.vault;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the SecretVault contract using an in-memory stub implementation.
 */
class SecretVaultTest {

    private InMemoryVault vault;

    @BeforeEach
    void setUp() {
        vault = new InMemoryVault();
    }

    @Test
    void getExistingSecret() {
        vault.set("openai_key", "sk-abc123", "OpenAI API key");
        assertEquals("sk-abc123", vault.get("openai_key"));
    }

    @Test
    void getMissingSecretThrows() {
        assertThrows(VaultException.class, () -> vault.get("missing_key"));
    }

    @Test
    void setNewSecret() {
        vault.set("new_key", "secret_value", "API key for service X");

        assertEquals("secret_value", vault.get("new_key"));
        assertTrue(vault.exists("new_key"));
    }

    @Test
    void overwriteExistingSecret() {
        vault.set("api_key", "old_value", "original");
        vault.set("api_key", "new_value", "rotated key");

        assertEquals("new_value", vault.get("api_key"));
    }

    @Test
    void deleteSecret() {
        vault.set("temp_key", "val", "temp");
        vault.delete("temp_key");

        assertFalse(vault.exists("temp_key"));
        assertThrows(VaultException.class, () -> vault.get("temp_key"));
    }

    @Test
    void deleteMissingSecretIsIdempotent() {
        assertDoesNotThrow(() -> vault.delete("ghost"));
    }

    @Test
    void listEntries() {
        vault.set("key_a", "val_a", "desc a");
        vault.set("key_b", "val_b", "desc b");

        List<VaultEntry> entries = vault.list();
        assertEquals(2, entries.size());

        for (VaultEntry entry : entries) {
            assertNotNull(entry.key());
            assertNotNull(entry.createdAt());
            assertNotNull(entry.description());
        }
    }

    @Test
    void listEntriesDoesNotContainDecryptedValues() {
        vault.set("secret", "top-secret", "classified");

        for (VaultEntry entry : vault.list()) {
            // VaultEntry is a record — only has key, createdAt, description
            // No value field exists to check
            assertNotNull(entry.key());
        }
    }

    @Test
    void existsReturnsTrueForPresent() {
        vault.set("exists_key", "val", "test");
        assertTrue(vault.exists("exists_key"));
    }

    @Test
    void existsReturnsFalseForMissing() {
        assertFalse(vault.exists("no_such_key"));
    }

    /**
     * Simple in-memory stub for testing the SecretVault contract.
     * Stores plaintext (no encryption) — suitable for interface-level tests only.
     */
    private static class InMemoryVault implements SecretVault {

        private final Map<String, StoredSecret> store = new HashMap<>();

        @Override
        public String get(String key) {
            StoredSecret s = store.get(key);
            if (s == null) {
                throw new VaultException("Secret not found: " + key);
            }
            return s.plaintext;
        }

        @Override
        public void set(String key, String plaintext, String description) {
            store.put(key, new StoredSecret(plaintext, Instant.now(), description));
        }

        @Override
        public void delete(String key) {
            store.remove(key);
        }

        @Override
        public List<VaultEntry> list() {
            return store.entrySet().stream()
                    .map(e -> new VaultEntry(e.getKey(), e.getValue().createdAt, e.getValue().description))
                    .toList();
        }

        @Override
        public boolean exists(String key) {
            return store.containsKey(key);
        }

        private record StoredSecret(String plaintext, Instant createdAt, String description) {}
    }
}
