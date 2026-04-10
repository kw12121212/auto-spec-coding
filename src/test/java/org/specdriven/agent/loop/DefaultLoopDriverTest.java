package org.specdriven.agent.loop;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.event.SimpleEventBus;

class DefaultLoopDriverTest {

    private SimpleEventBus eventBus() {
        return new SimpleEventBus();
    }

    @Test
    void initialStateIsIdle() {
        SimpleEventBus bus = eventBus();
        LoopConfig cfg = LoopConfig.defaults(Path.of("/tmp"), bus);
        LoopDriver driver = new DefaultLoopDriver(cfg, ctx -> Optional.empty());
        assertEquals(LoopState.IDLE, driver.getState());
    }

    @Test
    void startTransitionsToRunning() throws Exception {
        SimpleEventBus bus = eventBus();
        List<Event> events = new ArrayList<>();
        bus.subscribe(EventType.LOOP_STARTED, events::add);

        CountDownLatch done = new CountDownLatch(1);
        LoopConfig cfg = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus);
        LoopScheduler scheduler = ctx -> {
            done.countDown();
            return Optional.of(new LoopCandidate("test-change", "m1.md", "goal"));
        };
        LoopDriver driver = new DefaultLoopDriver(cfg, scheduler);
        driver.start();

        assertTrue(done.await(5, TimeUnit.SECONDS));
        Thread.sleep(100); // let loop finish
        assertEquals(LoopState.STOPPED, driver.getState());
        assertFalse(events.isEmpty());
        assertEquals(EventType.LOOP_STARTED, events.get(0).type());
    }

    @Test
    void startRejectsNonIdleState() {
        SimpleEventBus bus = eventBus();
        LoopConfig cfg = LoopConfig.defaults(Path.of("/tmp"), bus);
        LoopDriver driver = new DefaultLoopDriver(cfg, ctx -> Optional.empty());
        driver.start();
        assertThrows(IllegalStateException.class, driver::start);
    }

    @Test
    void stopTransitionsToStopped() throws Exception {
        SimpleEventBus bus = eventBus();
        List<Event> events = new ArrayList<>();
        bus.subscribe(EventType.LOOP_STOPPED, events::add);

        CountDownLatch started = new CountDownLatch(1);
        LoopConfig cfg = LoopConfig.defaults(Path.of("/tmp"), bus);
        LoopScheduler scheduler = ctx -> {
            started.countDown();
            try { Thread.sleep(10000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return Optional.of(new LoopCandidate("c", "m.md", "g"));
        };
        LoopDriver driver = new DefaultLoopDriver(cfg, scheduler);
        driver.start();
        assertTrue(started.await(5, TimeUnit.SECONDS));

        driver.stop();
        assertEquals(LoopState.STOPPED, driver.getState());
        assertFalse(events.isEmpty());
        assertEquals(EventType.LOOP_STOPPED, events.get(0).type());
    }

    @Test
    void stopIsIdempotent() {
        SimpleEventBus bus = eventBus();
        LoopConfig cfg = LoopConfig.defaults(Path.of("/tmp"), bus);
        LoopDriver driver = new DefaultLoopDriver(cfg, ctx -> Optional.empty());
        driver.start();
        driver.stop();
        assertDoesNotThrow(driver::stop); // second stop should not throw
        assertEquals(LoopState.STOPPED, driver.getState());
    }

    @Test
    void publishesIterationCompleted() throws Exception {
        SimpleEventBus bus = eventBus();
        List<Event> events = new ArrayList<>();
        bus.subscribe(EventType.LOOP_ITERATION_COMPLETED, events::add);

        CountDownLatch done = new CountDownLatch(1);
        LoopConfig cfg = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus);
        LoopScheduler scheduler = ctx -> {
            done.countDown();
            return Optional.of(new LoopCandidate("test-change", "m1.md", "goal"));
        };
        LoopDriver driver = new DefaultLoopDriver(cfg, scheduler);
        driver.start();

        assertTrue(done.await(5, TimeUnit.SECONDS));
        Thread.sleep(200); // let iteration complete

        assertFalse(events.isEmpty());
        assertEquals(EventType.LOOP_ITERATION_COMPLETED, events.get(0).type());
        assertEquals("LoopDriver", events.get(0).source());
    }

    @Test
    void maxIterationsReachedStopsLoop() throws Exception {
        SimpleEventBus bus = eventBus();
        List<Event> events = new ArrayList<>();
        bus.subscribe(EventType.LOOP_STOPPED, events::add);

        LoopConfig cfg = new LoopConfig(2, 60, List.of(), Path.of("/tmp"), bus);
        LoopScheduler scheduler = ctx ->
                Optional.of(new LoopCandidate("change-" + ctx.completedChangeNames().size(), "m.md", "g"));

        LoopDriver driver = new DefaultLoopDriver(cfg, scheduler);
        driver.start();

        Thread.sleep(1000); // wait for loop to finish

        assertEquals(LoopState.STOPPED, driver.getState());
        assertEquals(2, driver.getCompletedIterations().size());

        Event stopEvent = events.stream()
                .filter(e -> e.type() == EventType.LOOP_STOPPED)
                .findFirst().orElse(null);
        assertNotNull(stopEvent);
        assertEquals("max iterations reached", stopEvent.metadata().get("reason"));
    }

    @Test
    void noCandidatesStopsLoop() throws Exception {
        SimpleEventBus bus = eventBus();
        LoopConfig cfg = LoopConfig.defaults(Path.of("/tmp"), bus);
        LoopDriver driver = new DefaultLoopDriver(cfg, ctx -> Optional.empty());
        driver.start();

        Thread.sleep(500);
        assertEquals(LoopState.STOPPED, driver.getState());
    }

    @Test
    void pauseAndResume() throws Exception {
        SimpleEventBus bus = eventBus();
        List<Event> pausedEvents = new ArrayList<>();
        List<Event> resumedEvents = new ArrayList<>();
        bus.subscribe(EventType.LOOP_PAUSED, pausedEvents::add);
        bus.subscribe(EventType.LOOP_RESUMED, resumedEvents::add);

        CountDownLatch firstIter = new CountDownLatch(1);
        CountDownLatch allowSecond = new CountDownLatch(1);
        LoopConfig cfg = LoopConfig.defaults(Path.of("/tmp"), bus);
        LoopScheduler scheduler = new LoopScheduler() {
            int callCount = 0;
            @Override
            public Optional<LoopCandidate> selectNext(LoopContext context) {
                callCount++;
                if (callCount == 1) firstIter.countDown();
                try { allowSecond.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return Optional.of(new LoopCandidate("c" + callCount, "m.md", "g"));
            }
        };

        LoopDriver driver = new DefaultLoopDriver(cfg, scheduler);
        driver.start();
        assertTrue(firstIter.await(5, TimeUnit.SECONDS));
        Thread.sleep(50);

        driver.pause();
        assertEquals(LoopState.PAUSED, driver.getState());
        assertFalse(pausedEvents.isEmpty());

        driver.resume();
        assertEquals(LoopState.RECOMMENDING, driver.getState());
        assertFalse(resumedEvents.isEmpty());

        allowSecond.countDown();
        Thread.sleep(200);
        driver.stop();
    }

    @Test
    void getCompletedIterationsReturnsImmutableCopy() throws Exception {
        SimpleEventBus bus = eventBus();
        CountDownLatch done = new CountDownLatch(1);
        LoopConfig cfg = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus);
        LoopScheduler scheduler = ctx -> {
            done.countDown();
            return Optional.of(new LoopCandidate("c", "m.md", "g"));
        };
        LoopDriver driver = new DefaultLoopDriver(cfg, scheduler);
        driver.start();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        Thread.sleep(200);

        assertThrows(UnsupportedOperationException.class,
                () -> driver.getCompletedIterations().add(null));
    }

    @Test
    void eventBusFailureDoesNotPropagate() throws Exception {
        SimpleEventBus bus = new SimpleEventBus() {
            @Override
            public void publish(Event event) {
                if (event.type() == EventType.LOOP_STARTED) {
                    throw new RuntimeException("bus failure");
                }
            }
        };

        CountDownLatch done = new CountDownLatch(1);
        LoopConfig cfg = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus);
        LoopScheduler scheduler = ctx -> {
            done.countDown();
            return Optional.of(new LoopCandidate("c", "m.md", "g"));
        };
        LoopDriver driver = new DefaultLoopDriver(cfg, scheduler);

        assertDoesNotThrow(driver::start);
        assertTrue(done.await(5, TimeUnit.SECONDS));
    }

    @Test
    void schedulerExceptionTransitionsToError() throws Exception {
        SimpleEventBus bus = eventBus();
        LoopConfig cfg = LoopConfig.defaults(Path.of("/tmp"), bus);
        LoopScheduler scheduler = ctx -> { throw new RuntimeException("scheduler boom"); };
        LoopDriver driver = new DefaultLoopDriver(cfg, scheduler);
        driver.start();

        Thread.sleep(500);
        LoopState state = driver.getState();
        assertTrue(state == LoopState.ERROR || state == LoopState.STOPPED,
                "Expected ERROR or STOPPED but got " + state);
    }

    // -------------------------------------------------------------------------
    // Persistence integration tests
    // -------------------------------------------------------------------------

    private LealoneLoopIterationStore createStore(SimpleEventBus bus) {
        String dbName = "test_driver_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String jdbcUrl = "jdbc:lealone:embed:" + dbName + "?PERSISTENT=false";
        return new LealoneLoopIterationStore(bus, jdbcUrl);
    }

    @Test
    void startWithPriorProgressRecoversCompletedChanges() throws Exception {
        SimpleEventBus bus = eventBus();
        LealoneLoopIterationStore store = createStore(bus);

        // Pre-populate the store with prior progress
        store.saveProgress(new LoopProgress(LoopState.STOPPED, Set.of("already-done"), 1));
        store.saveIteration(new LoopIteration(1, "already-done", "m.md",
                100L, 200L, IterationStatus.SUCCESS, null));

        CountDownLatch done = new CountDownLatch(1);
        LoopConfig cfg = new LoopConfig(2, 60, List.of(), Path.of("/tmp"), bus);
        LoopScheduler scheduler = ctx -> {
            // Verify that completedChangeNames contains the recovered name
            assertTrue(ctx.completedChangeNames().contains("already-done"));
            done.countDown();
            return Optional.of(new LoopCandidate("new-change", "m.md", "goal"));
        };
        LoopDriver driver = new DefaultLoopDriver(cfg, scheduler, new StubLoopPipeline(), store);
        driver.start();

        assertTrue(done.await(5, TimeUnit.SECONDS));
        Thread.sleep(200);

        assertEquals(2, driver.getCompletedIterations().size());
        assertEquals("already-done", driver.getCompletedIterations().get(0).changeName());
        assertEquals("new-change", driver.getCompletedIterations().get(1).changeName());
    }

    @Test
    void iterationCompletionPersistsToStore() throws Exception {
        SimpleEventBus bus = eventBus();
        LealoneLoopIterationStore store = createStore(bus);

        CountDownLatch done = new CountDownLatch(1);
        LoopConfig cfg = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus);
        LoopScheduler scheduler = ctx -> {
            done.countDown();
            return Optional.of(new LoopCandidate("persisted-change", "m.md", "goal"));
        };
        LoopDriver driver = new DefaultLoopDriver(cfg, scheduler, new StubLoopPipeline(), store);
        driver.start();

        assertTrue(done.await(5, TimeUnit.SECONDS));
        Thread.sleep(200);

        // Check that the store has the iteration
        List<LoopIteration> iterations = store.loadIterations();
        assertEquals(1, iterations.size());
        assertEquals("persisted-change", iterations.get(0).changeName());

        // Check that progress was saved
        Optional<LoopProgress> progress = store.loadProgress();
        assertTrue(progress.isPresent());
        assertTrue(progress.get().completedChangeNames().contains("persisted-change"));
    }

    @Test
    void stopPersistsFinalSnapshot() throws Exception {
        SimpleEventBus bus = eventBus();
        LealoneLoopIterationStore store = createStore(bus);

        CountDownLatch started = new CountDownLatch(1);
        LoopConfig cfg = LoopConfig.defaults(Path.of("/tmp"), bus);
        LoopScheduler scheduler = ctx -> {
            started.countDown();
            try { Thread.sleep(10000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return Optional.of(new LoopCandidate("c", "m.md", "g"));
        };
        LoopDriver driver = new DefaultLoopDriver(cfg, scheduler, new StubLoopPipeline(), store);
        driver.start();
        assertTrue(started.await(5, TimeUnit.SECONDS));

        driver.stop();
        Thread.sleep(100);

        Optional<LoopProgress> progress = store.loadProgress();
        assertTrue(progress.isPresent());
        assertEquals(LoopState.STOPPED, progress.get().loopState());
    }

    @Test
    void nullStoreFallsBackToInMemoryBehavior() throws Exception {
        SimpleEventBus bus = eventBus();
        CountDownLatch done = new CountDownLatch(1);
        LoopConfig cfg = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus);
        LoopScheduler scheduler = ctx -> {
            done.countDown();
            return Optional.of(new LoopCandidate("c", "m.md", "g"));
        };
        LoopDriver driver = new DefaultLoopDriver(cfg, scheduler, new StubLoopPipeline(), null);
        driver.start();

        assertTrue(done.await(5, TimeUnit.SECONDS));
        Thread.sleep(200);

        assertEquals(LoopState.STOPPED, driver.getState());
        assertEquals(1, driver.getCompletedIterations().size());
    }

    @Test
    void existingConstructorsWorkWithoutStore() throws Exception {
        SimpleEventBus bus = eventBus();

        // Two-arg constructor
        LoopConfig cfg = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus);
        CountDownLatch done1 = new CountDownLatch(1);
        LoopDriver driver1 = new DefaultLoopDriver(cfg, ctx -> {
            done1.countDown();
            return Optional.of(new LoopCandidate("c", "m.md", "g"));
        });
        driver1.start();
        assertTrue(done1.await(5, TimeUnit.SECONDS));
        Thread.sleep(200);
        assertEquals(LoopState.STOPPED, driver1.getState());

        // Three-arg constructor
        CountDownLatch done2 = new CountDownLatch(1);
        LoopDriver driver2 = new DefaultLoopDriver(cfg, ctx -> {
            done2.countDown();
            return Optional.of(new LoopCandidate("c", "m.md", "g"));
        }, new StubLoopPipeline());
        driver2.start();
        assertTrue(done2.await(5, TimeUnit.SECONDS));
        Thread.sleep(200);
        assertEquals(LoopState.STOPPED, driver2.getState());
    }

    // -------------------------------------------------------------------------
    // Context exhaustion tests
    // -------------------------------------------------------------------------

    /**
     * Pipeline that simulates token usage by returning a configurable
     * tokenUsage value in its IterationResult.
     */
    private static class TokenSimulatingPipeline implements LoopPipeline {
        private final AtomicLong tokensPerIteration;

        TokenSimulatingPipeline(long tokensPerIteration) {
            this.tokensPerIteration = new AtomicLong(tokensPerIteration);
        }

        void setTokensPerIteration(long value) {
            tokensPerIteration.set(value);
        }

        @Override
        public IterationResult execute(LoopCandidate candidate, LoopConfig config) {
            return new IterationResult(IterationStatus.SUCCESS, null, 10,
                    List.of(PipelinePhase.PROPOSE, PipelinePhase.IMPLEMENT,
                            PipelinePhase.VERIFY, PipelinePhase.REVIEW, PipelinePhase.ARCHIVE),
                    tokensPerIteration.get());
        }
    }

    @Test
    void contextExhaustionStopsLoopAndPublishesEvent() throws Exception {
        SimpleEventBus bus = eventBus();
        List<Event> exhaustedEvents = new ArrayList<>();
        List<Event> stoppedEvents = new ArrayList<>();
        bus.subscribe(EventType.LOOP_CONTEXT_EXHAUSTED, exhaustedEvents::add);
        bus.subscribe(EventType.LOOP_STOPPED, stoppedEvents::add);

        // Budget: 1000 tokens, 20% threshold → exhausts when remaining < 200
        ContextBudget budget = ContextBudget.of(1000, 20);
        LoopConfig cfg = new LoopConfig(10, 60, List.of(), Path.of("/tmp"), bus, budget);

        // Each iteration uses 300 tokens → after 3 iterations = 900 used, remaining = 100 < 200
        TokenSimulatingPipeline pipeline = new TokenSimulatingPipeline(300);

        LoopScheduler scheduler = ctx ->
                Optional.of(new LoopCandidate("change-" + ctx.completedChangeNames().size(), "m.md", "g"));

        LoopDriver driver = new DefaultLoopDriver(cfg, scheduler, pipeline);
        driver.start();

        Thread.sleep(2000);

        assertEquals(LoopState.STOPPED, driver.getState());
        assertFalse(exhaustedEvents.isEmpty());
        assertEquals(EventType.LOOP_CONTEXT_EXHAUSTED, exhaustedEvents.get(0).type());

        Event stopEvent = stoppedEvents.stream()
                .filter(e -> "context exhausted".equals(e.metadata().get("reason")))
                .findFirst().orElse(null);
        assertNotNull(stopEvent);
    }

    @Test
    void contextExhaustionSavesProgressWithTokenUsage() throws Exception {
        SimpleEventBus bus = eventBus();
        LealoneLoopIterationStore store = createStore(bus);

        ContextBudget budget = ContextBudget.of(1000, 20);
        LoopConfig cfg = new LoopConfig(10, 60, List.of(), Path.of("/tmp"), bus, budget);

        TokenSimulatingPipeline pipeline = new TokenSimulatingPipeline(300);
        LoopScheduler scheduler = ctx ->
                Optional.of(new LoopCandidate("change-" + ctx.completedChangeNames().size(), "m.md", "g"));

        LoopDriver driver = new DefaultLoopDriver(cfg, scheduler, pipeline, store);
        driver.start();

        Thread.sleep(2000);

        Optional<LoopProgress> progress = store.loadProgress();
        assertTrue(progress.isPresent());
        assertTrue(progress.get().tokenUsage() > 0);
        assertEquals(LoopState.STOPPED, progress.get().loopState());
    }

    @Test
    void contextResumeRestoresTokenUsage() throws Exception {
        SimpleEventBus bus = eventBus();
        LealoneLoopIterationStore store = createStore(bus);

        // Pre-populate store with prior progress that has token usage
        store.saveProgress(new LoopProgress(LoopState.STOPPED, Set.of("already-done"), 1, 500));
        store.saveIteration(new LoopIteration(1, "already-done", "m.md",
                100L, 200L, IterationStatus.SUCCESS, null));

        // Budget: 1000 tokens, 20% threshold → 500 already used, remaining = 500
        // One iteration of 400 tokens → 900 used, remaining = 100 < 200 → exhaustion
        ContextBudget budget = ContextBudget.of(1000, 20);
        LoopConfig cfg = new LoopConfig(10, 60, List.of(), Path.of("/tmp"), bus, budget);

        TokenSimulatingPipeline pipeline = new TokenSimulatingPipeline(400);
        CountDownLatch done = new CountDownLatch(1);
        LoopScheduler scheduler = ctx -> {
            assertTrue(ctx.completedChangeNames().contains("already-done"));
            done.countDown();
            return Optional.of(new LoopCandidate("new-change", "m.md", "goal"));
        };

        LoopDriver driver = new DefaultLoopDriver(cfg, scheduler, pipeline, store);
        driver.start();

        assertTrue(done.await(5, TimeUnit.SECONDS));
        Thread.sleep(1000);

        assertEquals(LoopState.STOPPED, driver.getState());
        assertEquals(2, driver.getCompletedIterations().size());
    }

    @Test
    void nullBudgetProducesIdenticalBehaviorToPreChange() throws Exception {
        SimpleEventBus bus = eventBus();
        List<Event> exhaustedEvents = new ArrayList<>();
        bus.subscribe(EventType.LOOP_CONTEXT_EXHAUSTED, exhaustedEvents::add);

        // null budget = no context tracking
        LoopConfig cfg = new LoopConfig(2, 60, List.of(), Path.of("/tmp"), bus, null);

        // Pipeline that uses lots of tokens — but shouldn't matter without budget
        TokenSimulatingPipeline pipeline = new TokenSimulatingPipeline(999999);
        LoopScheduler scheduler = ctx ->
                Optional.of(new LoopCandidate("change-" + ctx.completedChangeNames().size(), "m.md", "g"));

        LoopDriver driver = new DefaultLoopDriver(cfg, scheduler, pipeline);
        driver.start();

        Thread.sleep(1000);

        assertEquals(LoopState.STOPPED, driver.getState());
        assertEquals(2, driver.getCompletedIterations().size());
        assertTrue(exhaustedEvents.isEmpty()); // no exhaustion event
    }
}
