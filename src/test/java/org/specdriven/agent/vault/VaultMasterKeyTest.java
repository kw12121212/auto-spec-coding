package org.specdriven.agent.vault;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VaultMasterKeyTest {

    @AfterEach
    void tearDown() {
        VaultMasterKey.reset();
    }

    @Test
    void defaultFallbackWhenEnvVarNotSet() {
        VaultMasterKey.setEnvSource(() -> null);

        String key = VaultMasterKey.get();

        assertEquals(VaultMasterKey.DEFAULT_KEY, key);
        assertTrue(VaultMasterKey.isDefault());
    }

    @Test
    void isDefaultReturnsFalseWhenEnvVarSet() {
        VaultMasterKey.setEnvSource(() -> "my-production-key");

        String key = VaultMasterKey.get();

        assertEquals("my-production-key", key);
        assertFalse(VaultMasterKey.isDefault());
    }

    @Test
    void readsFromEnvVar() {
        VaultMasterKey.setEnvSource(() -> "custom-key-123");

        assertEquals("custom-key-123", VaultMasterKey.get());
    }

    @Test
    void emptyEnvVarTreatedAsMissing() {
        VaultMasterKey.setEnvSource(() -> "");

        assertEquals(VaultMasterKey.DEFAULT_KEY, VaultMasterKey.get());
        assertTrue(VaultMasterKey.isDefault());
    }

    @Test
    void cachesResultAcrossCalls() {
        VaultMasterKey.setEnvSource(() -> "cached-key");

        String first = VaultMasterKey.get();
        String second = VaultMasterKey.get();

        assertSame(first, second);
    }
}
