package org.specdriven.agent.vault;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class VaultEntryTest {

    @Test
    void recordAccessors() {
        Instant now = Instant.now();
        VaultEntry entry = new VaultEntry("my_key", now, "test key");

        assertEquals("my_key", entry.key());
        assertEquals(now, entry.createdAt());
        assertEquals("test key", entry.description());
    }

    @Test
    void equality() {
        Instant now = Instant.now();
        VaultEntry a = new VaultEntry("k", now, "desc");
        VaultEntry b = new VaultEntry("k", now, "desc");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void nullDescriptionAllowed() {
        VaultEntry entry = new VaultEntry("k", Instant.now(), null);
        assertNull(entry.description());
    }
}
