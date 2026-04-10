package org.specdriven.agent.loop;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;

class LealoneLoopIterationStoreTest {

    private String jdbcUrl;
    private CapturingEventBus eventBus;
    private LealoneLoopIterationStore store;

    @BeforeEach
    void setUp() {
        String dbName = "test_loop_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        jdbcUrl = "jdbc:lealone:embed:" + dbName + "?PERSISTENT=false";
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
                Set.of("change-a", "change-b"), 2);
        store.saveProgress(progress);

        Optional<LoopProgress> loaded = store.loadProgress();
        assertTrue(loaded.isPresent());
        assertEquals(LoopState.RUNNING, loaded.get().loopState());
        assertEquals(2, loaded.get().totalIterations());
        assertEquals(Set.of("change-a", "change-b"), loaded.get().completedChangeNames());
    }

    @Test
    void loadProgressReturnsEmptyWhenNoData() {
        assertTrue(store.loadProgress().isEmpty());
    }

    @Test
    void saveProgressOverwritesPrevious() {
        store.saveProgress(new LoopProgress(LoopState.RUNNING, Set.of("a"), 1));
        store.saveProgress(new LoopProgress(LoopState.STOPPED, Set.of("a", "b"), 2));

        LoopProgress loaded = store.loadProgress().orElseThrow();
        assertEquals(LoopState.STOPPED, loaded.loopState());
        assertEquals(2, loaded.totalIterations());
        assertEquals(Set.of("a", "b"), loaded.completedChangeNames());
    }

    @Test
    void emptyCompletedChangeNamesRoundTrip() {
        store.saveProgress(new LoopProgress(LoopState.IDLE, Set.of(), 0));

        LoopProgress loaded = store.loadProgress().orElseThrow();
        assertTrue(loaded.completedChangeNames().isEmpty());
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

        assertFalse(eventBus.captured.isEmpty());
        Event evt = eventBus.captured.get(0);
        assertEquals(EventType.LOOP_PROGRESS_SAVED, evt.type());
        assertEquals("LoopIterationStore", evt.source());
        assertEquals(1, evt.metadata().get("iterationCount"));
        assertEquals(1, evt.metadata().get("completedChangeCount"));
    }

    // --- Capturing EventBus ---

    private static class CapturingEventBus implements EventBus {
        final List<Event> captured = new ArrayList<>();

        @Override
        public void publish(Event event) {
            captured.add(event);
        }

        @Override
        public void subscribe(EventType type, java.util.function.Consumer<Event> handler) {}

        @Override
        public void unsubscribe(EventType type, java.util.function.Consumer<Event> handler) {}
    }
}
