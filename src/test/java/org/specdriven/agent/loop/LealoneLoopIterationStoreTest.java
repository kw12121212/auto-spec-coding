package org.specdriven.agent.loop;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.testsupport.CapturingEventBus;
import org.specdriven.agent.testsupport.LealoneTestDb;

class LealoneLoopIterationStoreTest {

    private String jdbcUrl;
    private CapturingEventBus eventBus;
    private LealoneLoopIterationStore store;

    @BeforeEach
    void setUp() {
        jdbcUrl = LealoneTestDb.freshJdbcUrl();
        eventBus = new CapturingEventBus();
        store = new LealoneLoopIterationStore(eventBus, jdbcUrl);
    }

    // --- saveIteration / loadIterations ---

    @Test
    void saveAndLoadIteration() {
        LoopIteration iter = new LoopIteration(1, "change-a", "m1.md",
                System.currentTimeMillis(), System.currentTimeMillis(),
                IterationStatus.SUCCESS, null);
        store.saveIteration(iter);

        List<LoopIteration> loaded = store.loadIterations();
        assertEquals(1, loaded.size());
        assertEquals("change-a", loaded.get(0).changeName());
        assertEquals(IterationStatus.SUCCESS, loaded.get(0).status());
    }

    @Test
    void loadIterationsOrderedByNumber() {
        store.saveIteration(new LoopIteration(3, "c", "m.md",
                100L, 200L, IterationStatus.SUCCESS, null));
        store.saveIteration(new LoopIteration(1, "a", "m.md",
                100L, 200L, IterationStatus.SUCCESS, null));
        store.saveIteration(new LoopIteration(2, "b", "m.md",
                100L, 200L, IterationStatus.FAILED, "error"));

        List<LoopIteration> loaded = store.loadIterations();
        assertEquals(3, loaded.size());
        assertEquals(1, loaded.get(0).iterationNumber());
        assertEquals(2, loaded.get(1).iterationNumber());
        assertEquals(3, loaded.get(2).iterationNumber());
    }

    @Test
    void mergeOverwritesExistingIteration() {
        store.saveIteration(new LoopIteration(1, "old", "m.md",
                100L, 200L, IterationStatus.SUCCESS, null));
        store.saveIteration(new LoopIteration(1, "updated", "m.md",
                100L, 300L, IterationStatus.FAILED, "reason"));

        List<LoopIteration> loaded = store.loadIterations();
        assertEquals(1, loaded.size());
        assertEquals("updated", loaded.get(0).changeName());
        assertEquals(IterationStatus.FAILED, loaded.get(0).status());
        assertEquals("reason", loaded.get(0).failureReason());
    }

    @Test
    void nullCompletedAtAndFailureReasonPreserved() {
        store.saveIteration(new LoopIteration(1, "c", "m.md",
                100L, null, IterationStatus.SUCCESS, null));

        LoopIteration loaded = store.loadIterations().get(0);
        assertNull(loaded.completedAt());
        assertNull(loaded.failureReason());
    }

    // --- saveProgress / loadProgress ---

    @Test
    void saveAndLoadProgress() {
        LoopProgress progress = new LoopProgress(LoopState.RUNNING,
                Set.of("change-a", "change-b"), 2, 1500);
        store.saveProgress(progress);

        Optional<LoopProgress> loaded = store.loadProgress();
        assertTrue(loaded.isPresent());
        assertEquals(LoopState.RUNNING, loaded.get().loopState());
        assertEquals(2, loaded.get().totalIterations());
        assertEquals(Set.of("change-a", "change-b"), loaded.get().completedChangeNames());
        assertEquals(1500, loaded.get().tokenUsage());
    }

    @Test
    void loadProgressReturnsEmptyWhenNoData() {
        assertTrue(store.loadProgress().isEmpty());
    }

    @Test
    void saveProgressOverwritesPrevious() {
        store.saveProgress(new LoopProgress(LoopState.RUNNING, Set.of("a"), 1, 100));
        store.saveProgress(new LoopProgress(LoopState.STOPPED, Set.of("a", "b"), 2, 500));

        LoopProgress loaded = store.loadProgress().orElseThrow();
        assertEquals(LoopState.STOPPED, loaded.loopState());
        assertEquals(2, loaded.totalIterations());
        assertEquals(Set.of("a", "b"), loaded.completedChangeNames());
        assertEquals(500, loaded.tokenUsage());
    }

    @Test
    void emptyCompletedChangeNamesRoundTrip() {
        store.saveProgress(new LoopProgress(LoopState.IDLE, Set.of(), 0));

        LoopProgress loaded = store.loadProgress().orElseThrow();
        assertTrue(loaded.completedChangeNames().isEmpty());
        assertEquals(0, loaded.tokenUsage());
    }

    @Test
    void backwardCompatThreeArgConstructorDefaultsTokenUsageToZero() {
        // Using old 3-arg constructor — tokenUsage should default to 0
        LoopProgress progress = new LoopProgress(LoopState.RUNNING, Set.of("a"), 1);
        assertEquals(0, progress.tokenUsage());
        store.saveProgress(progress);

        LoopProgress loaded = store.loadProgress().orElseThrow();
        assertEquals(0, loaded.tokenUsage());
    }

    @Test
    void checkpointRoundTripPreservesPhaseData() {
        LoopPhaseCheckpoint checkpoint = new LoopPhaseCheckpoint(
                "change-a", "m35.md", "goal", "summary",
                List.of(PipelinePhase.VERIFY, PipelinePhase.RECOMMEND));
        store.saveProgress(new LoopProgress(LoopState.RUNNING, Set.of("done"), 1, 400,
                checkpoint));

        LoopProgress loaded = store.loadProgress().orElseThrow();
        assertTrue(loaded.activeCheckpoint().isPresent());
        LoopPhaseCheckpoint loadedCheckpoint = loaded.activeCheckpoint().orElseThrow();
        assertEquals("change-a", loadedCheckpoint.changeName());
        assertEquals("m35.md", loadedCheckpoint.milestoneFile());
        assertEquals("goal", loadedCheckpoint.milestoneGoal());
        assertEquals("summary", loadedCheckpoint.plannedChangeSummary());
        assertEquals(List.of(PipelinePhase.RECOMMEND, PipelinePhase.VERIFY),
                loadedCheckpoint.completedPhases());
        assertEquals(400, loaded.tokenUsage());
    }

    @Test
    void saveProgressWithoutCheckpointClearsPreviousCheckpoint() {
        LoopPhaseCheckpoint checkpoint = new LoopPhaseCheckpoint(
                "change-a", "m35.md", "goal", "summary",
                List.of(PipelinePhase.RECOMMEND));
        store.saveProgress(new LoopProgress(LoopState.RUNNING, Set.of(), 0, 0, checkpoint));
        store.saveProgress(new LoopProgress(LoopState.STOPPED, Set.of("change-a"), 1, 0));

        LoopProgress loaded = store.loadProgress().orElseThrow();
        assertTrue(loaded.activeCheckpoint().isEmpty());
        assertTrue(loaded.completedChangeNames().contains("change-a"));
    }

    @Test
    void oldProgressSnapshotLoadsWithoutCheckpoint() throws Exception {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "root", "");
             PreparedStatement ps = conn.prepareStatement("""
                     MERGE INTO loop_progress (
                         id, loop_state, completed_change_names, total_iterations, token_usage, updated_at
                     ) VALUES (?, ?, ?, ?, ?, ?)
                     """)) {
            ps.setInt(1, 1);
            ps.setString(2, LoopState.RUNNING.name());
            ps.setString(3, LealoneLoopIterationStore.setToJson(Set.of("done")));
            ps.setInt(4, 3);
            ps.setLong(5, 700);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        }

        LoopProgress loaded = store.loadProgress().orElseThrow();
        assertEquals(LoopState.RUNNING, loaded.loopState());
        assertEquals(Set.of("done"), loaded.completedChangeNames());
        assertEquals(3, loaded.totalIterations());
        assertEquals(700, loaded.tokenUsage());
        assertTrue(loaded.activeCheckpoint().isEmpty());
    }

    @Test
    void oldProgressTableSchemaIsUpgradedAndLoadsWithoutCheckpoint() throws Exception {
        String oldJdbcUrl = LealoneTestDb.freshJdbcUrl();
        try (Connection conn = DriverManager.getConnection(oldJdbcUrl, "root", "");
             var stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE loop_progress (
                        id INT PRIMARY KEY,
                        loop_state VARCHAR(20) NOT NULL,
                        completed_change_names CLOB,
                        total_iterations INT NOT NULL DEFAULT 0,
                        token_usage BIGINT NOT NULL DEFAULT 0,
                        updated_at BIGINT NOT NULL
                    )
                    """);
            stmt.executeUpdate("""
                    INSERT INTO loop_progress (
                        id, loop_state, completed_change_names, total_iterations, token_usage, updated_at
                    ) VALUES (1, 'RUNNING', '["done"]', 4, 900, 1)
                    """);
        }

        LealoneLoopIterationStore upgraded = new LealoneLoopIterationStore(eventBus, oldJdbcUrl);
        LoopProgress loaded = upgraded.loadProgress().orElseThrow();

        assertEquals(LoopState.RUNNING, loaded.loopState());
        assertEquals(Set.of("done"), loaded.completedChangeNames());
        assertEquals(4, loaded.totalIterations());
        assertEquals(900, loaded.tokenUsage());
        assertTrue(loaded.activeCheckpoint().isEmpty());
    }

    // --- clear ---

    @Test
    void clearRemovesAll() {
        store.saveIteration(new LoopIteration(1, "c", "m.md",
                100L, 200L, IterationStatus.SUCCESS, null));
        store.saveProgress(new LoopProgress(LoopState.RUNNING, Set.of("c"), 1));

        store.clear();

        assertTrue(store.loadIterations().isEmpty());
        assertTrue(store.loadProgress().isEmpty());
    }

    // --- JSON round-trip for change names ---

    @Test
    void jsonRoundTripPreservesNames() {
        Set<String> names = Set.of("alpha", "beta", "gamma");
        String json = LealoneLoopIterationStore.setToJson(names);
        Set<String> restored = LealoneLoopIterationStore.jsonToSet(json);
        assertEquals(names, restored);
    }

    @Test
    void jsonRoundTripEmptySet() {
        String json = LealoneLoopIterationStore.setToJson(Set.of());
        assertEquals("[]", json);
        assertTrue(LealoneLoopIterationStore.jsonToSet(json).isEmpty());
    }

    @Test
    void jsonRoundTripNullSet() {
        String json = LealoneLoopIterationStore.setToJson(null);
        assertEquals("[]", json);
    }

    // --- EventBus integration ---

    @Test
    void saveProgressPublishesEvent() {
        store.saveProgress(new LoopProgress(LoopState.RUNNING, Set.of("a"), 1));

        assertFalse(eventBus.getEvents().isEmpty());
        Event evt = eventBus.getEvents().get(0);
        assertEquals(EventType.LOOP_PROGRESS_SAVED, evt.type());
        assertEquals("LoopIterationStore", evt.source());
        assertEquals(1, evt.metadata().get("iterationCount"));
        assertEquals(1, evt.metadata().get("completedChangeCount"));
    }

}
