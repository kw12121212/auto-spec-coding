package org.specdriven.agent.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.specdriven.agent.agent.LlmConfigSnapshot;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LealoneRuntimeLlmConfigStoreTest {

    private String jdbcUrl;
    private LealoneRuntimeLlmConfigStore store;

    @BeforeEach
    void setUp() {
        jdbcUrl = jdbcUrl("runtime_llm_config");
        store = new LealoneRuntimeLlmConfigStore(jdbcUrl);
    }

    @Test
    void loadDefaultSnapshot_returnsEmptyWhenNothingPersisted() {
        assertTrue(store.loadDefaultSnapshot().isEmpty());
    }

    @Test
    void persistDefaultSnapshot_storesNonSensitiveSnapshot() {
        LlmConfigSnapshot snapshot = snapshot("openai", "https://api.persisted.example/v1", "gpt-4.1", 30, 2);

        RuntimeLlmConfigVersion persisted = store.persistDefaultSnapshot(snapshot);

        assertEquals(1L, persisted.version());
        assertTrue(persisted.active());
        assertEquals(snapshot, store.loadDefaultSnapshot().orElseThrow());
    }

    @Test
    void newStoreOnSameBackingDatabase_recoversLatestPersistedSnapshot() {
        LlmConfigSnapshot snapshot = snapshot("openai", "https://api.persisted.example/v1", "gpt-4.1", 30, 2);
        store.persistDefaultSnapshot(snapshot);

        LealoneRuntimeLlmConfigStore recoveredStore = new LealoneRuntimeLlmConfigStore(jdbcUrl);

        assertEquals(snapshot, recoveredStore.loadDefaultSnapshot().orElseThrow());
    }

    @Test
    void listDefaultSnapshotVersions_returnsNewestFirst() {
        LlmConfigSnapshot first = snapshot("openai", "https://api.first.example/v1", "gpt-4", 20, 1);
        LlmConfigSnapshot second = snapshot("openai", "https://api.second.example/v1", "gpt-4.1", 25, 2);
        store.persistDefaultSnapshot(first);
        store.persistDefaultSnapshot(second);

        List<RuntimeLlmConfigVersion> versions = store.listDefaultSnapshotVersions();

        assertEquals(2, versions.size());
        assertEquals(second, versions.get(0).snapshot());
        assertEquals(first, versions.get(1).snapshot());
        assertTrue(versions.get(0).active());
        assertFalse(versions.get(1).active());
    }

    @Test
    void failedPersistDefaultSnapshot_doesNotExposePartialHistoryEntry() {
        LlmConfigSnapshot first = snapshot("openai", "https://api.first.example/v1", "gpt-4", 20, 1);
        store.persistDefaultSnapshot(first);

        LealoneRuntimeLlmConfigStore failingStore = new LealoneRuntimeLlmConfigStore(jdbcUrl, () -> {
            throw new IllegalStateException("boom");
        });
        LlmConfigSnapshot second = snapshot("openai", "https://api.second.example/v1", "gpt-4.1", 25, 2);

        assertThrows(IllegalStateException.class, () -> failingStore.persistDefaultSnapshot(second));
        assertEquals(first, store.loadDefaultSnapshot().orElseThrow());
        assertEquals(1, store.listDefaultSnapshotVersions().size());
    }

    @Test
    void restoreDefaultSnapshot_reactivatesEarlierVersionWithoutDeletingHistory() {
        LlmConfigSnapshot first = snapshot("openai", "https://api.first.example/v1", "gpt-4", 20, 1);
        LlmConfigSnapshot second = snapshot("openai", "https://api.second.example/v1", "gpt-4.1", 25, 2);
        LlmConfigSnapshot third = snapshot("openai", "https://api.third.example/v1", "gpt-4.1-mini", 15, 0);
        RuntimeLlmConfigVersion v1 = store.persistDefaultSnapshot(first);
        store.persistDefaultSnapshot(second);
        store.persistDefaultSnapshot(third);

        RuntimeLlmConfigVersion restored = store.restoreDefaultSnapshot(v1.version());
        List<RuntimeLlmConfigVersion> versions = store.listDefaultSnapshotVersions();

        assertEquals(first, restored.snapshot());
        assertEquals(first, store.loadDefaultSnapshot().orElseThrow());
        assertEquals(3, versions.size());
        assertTrue(versions.stream().anyMatch(version -> version.version() == v1.version() && version.active()));
    }

    private static LlmConfigSnapshot snapshot(String providerName, String baseUrl, String model, int timeout, int maxRetries) {
        return new LlmConfigSnapshot(providerName, baseUrl, model, timeout, maxRetries);
    }

    private static String jdbcUrl(String prefix) {
        String dbName = prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return "jdbc:lealone:embed:" + dbName + "?PERSISTENT=false";
    }
}
