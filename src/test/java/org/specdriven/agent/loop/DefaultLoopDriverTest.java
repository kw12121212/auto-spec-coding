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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.specdriven.agent.question.Answer;
import org.specdriven.agent.question.AnswerSource;
import org.specdriven.agent.question.DeliveryMode;
import org.specdriven.agent.question.InMemoryReplyCollector;
import org.specdriven.agent.question.Question;
import org.specdriven.agent.question.QuestionCategory;
import org.specdriven.agent.question.QuestionDecision;
import org.specdriven.agent.question.QuestionDeliveryChannel;
import org.specdriven.agent.question.QuestionDeliveryService;
import org.specdriven.agent.question.QuestionReplyCollector;
import org.specdriven.agent.question.QuestionRuntime;
import org.specdriven.agent.question.QuestionStatus;
import org.specdriven.agent.question.QuestionStore;
import org.specdriven.agent.interactive.InteractiveSession;
import org.specdriven.agent.interactive.InteractiveSessionState;

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
        waitUntilState(driver, LoopState.STOPPED);
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
        waitUntilNonEmpty(events);

        assertFalse(events.isEmpty());
        assertEquals(EventType.LOOP_ITERATION_COMPLETED, events.get(0).type());
        assertEquals("LoopDriver", events.get(0).source());
    }

    @Test
    void pipelineReceivesSchedulerSelectedCandidateWithoutReselection() throws Exception {
        SimpleEventBus bus = eventBus();
        LoopCandidate selected = new LoopCandidate("selected-change", "m1.md", "goal", "summary");
        AtomicInteger schedulerCalls = new AtomicInteger();
        AtomicReference<LoopCandidate> pipelineCandidate = new AtomicReference<>();
        CountDownLatch pipelineCalled = new CountDownLatch(1);

        LoopScheduler scheduler = ctx -> {
            schedulerCalls.incrementAndGet();
            return Optional.of(selected);
        };
        LoopPipeline pipeline = (candidate, config, skipPhases) -> {
            pipelineCandidate.set(candidate);
            pipelineCalled.countDown();
            return new IterationResult(IterationStatus.SUCCESS, null, 1,
                    List.of(PipelinePhase.RECOMMEND, PipelinePhase.PROPOSE,
                            PipelinePhase.IMPLEMENT, PipelinePhase.VERIFY,
                            PipelinePhase.REVIEW, PipelinePhase.ARCHIVE));
        };

        LoopConfig cfg = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus);
        DefaultLoopDriver driver = new DefaultLoopDriver(cfg, scheduler, pipeline);
        driver.start();

        assertTrue(pipelineCalled.await(5, TimeUnit.SECONDS));
        waitUntilState(driver, LoopState.STOPPED);

        assertEquals(1, schedulerCalls.get());
        assertEquals(selected, pipelineCandidate.get());
        assertEquals("summary", pipelineCandidate.get().plannedChangeSummary());
        assertEquals(LoopState.STOPPED, driver.getState());
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
        waitUntilState(driver, LoopState.STOPPED);

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
        waitUntilState(driver, LoopState.STOPPED);

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

        driver.pause();
        assertEquals(LoopState.PAUSED, driver.getState());
        assertFalse(pausedEvents.isEmpty());

        driver.resume();
        assertEquals(LoopState.RECOMMENDING, driver.getState());
        assertFalse(resumedEvents.isEmpty());

        allowSecond.countDown();
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
        waitUntilState(driver, LoopState.STOPPED);

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
        waitUntilStateIn(driver, LoopState.ERROR, LoopState.STOPPED);

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
        waitUntilState(driver, LoopState.STOPPED);

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
        waitUntilState(driver, LoopState.STOPPED);

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
    void startupResumesCheckpointedCandidateBeforeSelectingNewWork() throws Exception {
        SimpleEventBus bus = eventBus();
        LealoneLoopIterationStore store = createStore(bus);
        LoopPhaseCheckpoint checkpoint = new LoopPhaseCheckpoint(
                "checkpointed-change", "m35.md", "goal", "summary",
                List.of(PipelinePhase.RECOMMEND, PipelinePhase.PROPOSE));
        store.saveProgress(new LoopProgress(LoopState.PAUSED, Set.of(), 0, 0, checkpoint));

        AtomicInteger schedulerCalls = new AtomicInteger();
        AtomicReference<LoopCandidate> pipelineCandidate = new AtomicReference<>();
        AtomicReference<Set<PipelinePhase>> pipelineSkipPhases = new AtomicReference<>();
        CountDownLatch pipelineCalled = new CountDownLatch(1);
        LoopScheduler scheduler = ctx -> {
            schedulerCalls.incrementAndGet();
            return Optional.of(new LoopCandidate("new-change", "m.md", "goal"));
        };
        LoopPipeline pipeline = (candidate, config, skipPhases) -> {
            pipelineCandidate.set(candidate);
            pipelineSkipPhases.set(skipPhases);
            pipelineCalled.countDown();
            return new IterationResult(IterationStatus.SUCCESS, null, 10,
                    List.of(PipelinePhase.IMPLEMENT, PipelinePhase.VERIFY,
                            PipelinePhase.REVIEW, PipelinePhase.ARCHIVE));
        };

        LoopConfig cfg = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus);
        DefaultLoopDriver driver = new DefaultLoopDriver(cfg, scheduler, pipeline, store);
        driver.start();

        assertTrue(pipelineCalled.await(5, TimeUnit.SECONDS));
        waitUntilState(driver, LoopState.STOPPED);

        assertEquals(0, schedulerCalls.get());
        assertEquals("checkpointed-change", pipelineCandidate.get().changeName());
        assertEquals(Set.of(PipelinePhase.RECOMMEND, PipelinePhase.PROPOSE),
                pipelineSkipPhases.get());
        LoopProgress progress = store.loadProgress().orElseThrow();
        assertTrue(progress.activeCheckpoint().isEmpty());
        assertTrue(progress.completedChangeNames().contains("checkpointed-change"));
    }

    @Test
    void failedPhasePersistsRetryableCheckpointWithoutCompletingChange() throws Exception {
        SimpleEventBus bus = eventBus();
        LealoneLoopIterationStore store = createStore(bus);
        CountDownLatch pipelineCalled = new CountDownLatch(1);

        LoopPipeline pipeline = (candidate, config, skipPhases) -> {
            pipelineCalled.countDown();
            return new IterationResult(IterationStatus.FAILED, "verify failed", 10,
                    List.of(PipelinePhase.RECOMMEND, PipelinePhase.PROPOSE));
        };
        LoopScheduler scheduler = ctx ->
                Optional.of(new LoopCandidate("failed-change", "m35.md", "goal", "summary"));
        LoopConfig cfg = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus);
        DefaultLoopDriver driver = new DefaultLoopDriver(cfg, scheduler, pipeline, store);
        driver.start();

        assertTrue(pipelineCalled.await(5, TimeUnit.SECONDS));
        waitUntilState(driver, LoopState.STOPPED);

        LoopProgress progress = store.loadProgress().orElseThrow();
        assertFalse(progress.completedChangeNames().contains("failed-change"));
        LoopPhaseCheckpoint checkpoint = progress.activeCheckpoint().orElseThrow();
        assertEquals("failed-change", checkpoint.changeName());
        assertEquals("m35.md", checkpoint.milestoneFile());
        assertEquals(List.of(PipelinePhase.RECOMMEND, PipelinePhase.PROPOSE),
                checkpoint.completedPhases());
        assertEquals(1, store.loadIterations().size());
        assertEquals(IterationStatus.FAILED, store.loadIterations().get(0).status());
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
        waitUntilState(driver, LoopState.STOPPED);

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
        waitUntilState(driver, LoopState.STOPPED);

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
        waitUntilState(driver1, LoopState.STOPPED);
        assertEquals(LoopState.STOPPED, driver1.getState());

        // Three-arg constructor
        CountDownLatch done2 = new CountDownLatch(1);
        LoopDriver driver2 = new DefaultLoopDriver(cfg, ctx -> {
            done2.countDown();
            return Optional.of(new LoopCandidate("c", "m.md", "g"));
        }, new StubLoopPipeline());
        driver2.start();
        assertTrue(done2.await(5, TimeUnit.SECONDS));
        waitUntilState(driver2, LoopState.STOPPED);
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
        public IterationResult execute(LoopCandidate candidate, LoopConfig config, Set<PipelinePhase> skipPhases) {
            return new IterationResult(IterationStatus.SUCCESS, null, 10,
                    List.of(PipelinePhase.RECOMMEND, PipelinePhase.PROPOSE, PipelinePhase.IMPLEMENT,
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
        waitUntilState(driver, LoopState.STOPPED);

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
        waitUntilState(driver, LoopState.STOPPED);

        Optional<LoopProgress> progress = store.loadProgress();
        assertTrue(progress.isPresent());
        assertTrue(progress.get().tokenUsage() > 0);
        assertEquals(LoopState.STOPPED, progress.get().loopState());
    }

    @Test
    void progressPersistsCumulativeTokenUsageAcrossIterations() throws Exception {
        SimpleEventBus bus = eventBus();
        LealoneLoopIterationStore store = createStore(bus);

        ContextBudget budget = ContextBudget.of(5000, 20);
        LoopConfig cfg = new LoopConfig(2, 60, List.of(), Path.of("/tmp"), bus, budget);

        TokenSimulatingPipeline pipeline = new TokenSimulatingPipeline(300);
        LoopScheduler scheduler = ctx ->
                Optional.of(new LoopCandidate("change-" + ctx.completedChangeNames().size(), "m.md", "g"));

        LoopDriver driver = new DefaultLoopDriver(cfg, scheduler, pipeline, store);
        driver.start();
        waitUntilState(driver, LoopState.STOPPED);

        LoopProgress progress = store.loadProgress().orElseThrow();
        assertEquals(LoopState.STOPPED, progress.loopState());
        assertEquals(600, progress.tokenUsage());
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
        waitUntilState(driver, LoopState.STOPPED);

        assertEquals(LoopState.STOPPED, driver.getState());
        assertEquals(2, driver.getCompletedIterations().size());
    }

    @Test
    void checkpointRecoveryAddsOnlyNewTokenUsageToRecoveredBaseline() throws Exception {
        SimpleEventBus bus = eventBus();
        LealoneLoopIterationStore store = createStore(bus);
        LoopPhaseCheckpoint checkpoint = new LoopPhaseCheckpoint(
                "checkpointed-token-change", "m35.md", "goal", "summary",
                List.of(PipelinePhase.RECOMMEND, PipelinePhase.PROPOSE));
        store.saveProgress(new LoopProgress(LoopState.PAUSED, Set.of(), 0, 500, checkpoint));

        AtomicInteger schedulerCalls = new AtomicInteger();
        AtomicReference<Set<PipelinePhase>> skipped = new AtomicReference<>();
        CountDownLatch pipelineCalled = new CountDownLatch(1);
        LoopPipeline pipeline = (candidate, config, skipPhases) -> {
            skipped.set(skipPhases);
            pipelineCalled.countDown();
            return new IterationResult(IterationStatus.SUCCESS, null, 10,
                    List.of(PipelinePhase.IMPLEMENT, PipelinePhase.VERIFY,
                            PipelinePhase.REVIEW, PipelinePhase.ARCHIVE),
                    125);
        };
        LoopConfig cfg = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus,
                ContextBudget.of(5000, 20));
        DefaultLoopDriver driver = new DefaultLoopDriver(cfg, ctx -> {
            schedulerCalls.incrementAndGet();
            return Optional.of(new LoopCandidate("new-change", "m.md", "goal"));
        }, pipeline, store);
        driver.start();

        assertTrue(pipelineCalled.await(5, TimeUnit.SECONDS));
        waitUntilState(driver, LoopState.STOPPED);

        assertEquals(0, schedulerCalls.get());
        assertEquals(Set.of(PipelinePhase.RECOMMEND, PipelinePhase.PROPOSE), skipped.get());
        LoopProgress progress = store.loadProgress().orElseThrow();
        assertEquals(625, progress.tokenUsage());
        assertTrue(progress.activeCheckpoint().isEmpty());
        assertTrue(progress.completedChangeNames().contains("checkpointed-token-change"));
    }

    @Test
    void checkpointRecoveryDoesNotDoubleCountRecoveredBaselineWhenNoNewTokensAreUsed() throws Exception {
        SimpleEventBus bus = eventBus();
        LealoneLoopIterationStore store = createStore(bus);
        LoopPhaseCheckpoint checkpoint = new LoopPhaseCheckpoint(
                "zero-token-resume", "m35.md", "goal", "summary",
                List.of(PipelinePhase.RECOMMEND));
        store.saveProgress(new LoopProgress(LoopState.PAUSED, Set.of(), 0, 700, checkpoint));

        CountDownLatch pipelineCalled = new CountDownLatch(1);
        LoopPipeline pipeline = (candidate, config, skipPhases) -> {
            pipelineCalled.countDown();
            return new IterationResult(IterationStatus.SUCCESS, null, 10,
                    List.of(PipelinePhase.PROPOSE, PipelinePhase.IMPLEMENT,
                            PipelinePhase.VERIFY, PipelinePhase.REVIEW, PipelinePhase.ARCHIVE),
                    0);
        };
        LoopConfig cfg = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus,
                ContextBudget.of(5000, 20));
        DefaultLoopDriver driver = new DefaultLoopDriver(cfg, ctx -> Optional.empty(), pipeline, store);
        driver.start();

        assertTrue(pipelineCalled.await(5, TimeUnit.SECONDS));
        waitUntilState(driver, LoopState.STOPPED);

        LoopProgress progress = store.loadProgress().orElseThrow();
        assertEquals(700, progress.tokenUsage());
        assertTrue(progress.completedChangeNames().contains("zero-token-resume"));
        assertTrue(progress.activeCheckpoint().isEmpty());
    }

    @Test
    void failedPhaseAttemptPersistsTokenUsageWithRetryableCheckpoint() throws Exception {
        SimpleEventBus bus = eventBus();
        LealoneLoopIterationStore store = createStore(bus);
        CountDownLatch pipelineCalled = new CountDownLatch(1);

        LoopPipeline pipeline = (candidate, config, skipPhases) -> {
            pipelineCalled.countDown();
            return new IterationResult(IterationStatus.TIMED_OUT, "verify timed out", 10,
                    List.of(PipelinePhase.RECOMMEND, PipelinePhase.PROPOSE),
                    175);
        };
        LoopConfig cfg = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus,
                ContextBudget.of(5000, 20));
        DefaultLoopDriver driver = new DefaultLoopDriver(cfg,
                ctx -> Optional.of(new LoopCandidate("retry-token-change", "m35.md", "goal", "summary")),
                pipeline, store);
        driver.start();

        assertTrue(pipelineCalled.await(5, TimeUnit.SECONDS));
        waitUntilState(driver, LoopState.STOPPED);

        LoopProgress progress = store.loadProgress().orElseThrow();
        assertEquals(175, progress.tokenUsage());
        assertFalse(progress.completedChangeNames().contains("retry-token-change"));
        LoopPhaseCheckpoint checkpoint = progress.activeCheckpoint().orElseThrow();
        assertEquals("retry-token-change", checkpoint.changeName());
        assertEquals(List.of(PipelinePhase.RECOMMEND, PipelinePhase.PROPOSE),
                checkpoint.completedPhases());
    }

    @Test
    void humanQuestionContextExhaustionSavesRetryableCheckpointBeforeStopping() throws Exception {
        SimpleEventBus bus = eventBus();
        LealoneLoopIterationStore store = createStore(bus);
        List<Event> exhaustedEvents = new ArrayList<>();
        bus.subscribe(EventType.LOOP_CONTEXT_EXHAUSTED, exhaustedEvents::add);

        Question question = humanEscalatedQuestion("q-budget", "s-budget");
        LoopPipeline pipeline = (candidate, config, skipPhases) ->
                new IterationResult(IterationStatus.QUESTIONING, null, 10,
                        List.of(PipelinePhase.RECOMMEND), 850, question);
        LoopConfig cfg = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus,
                ContextBudget.of(1000, 20));
        DefaultLoopDriver driver = new DefaultLoopDriver(cfg,
                ctx -> Optional.of(new LoopCandidate("budget-question-change", "m35.md", "goal", "summary")),
                pipeline, store, null);
        driver.start();

        waitUntilState(driver, LoopState.STOPPED);

        LoopProgress progress = store.loadProgress().orElseThrow();
        assertEquals(LoopState.STOPPED, progress.loopState());
        assertEquals(850, progress.tokenUsage());
        assertFalse(progress.completedChangeNames().contains("budget-question-change"));
        LoopPhaseCheckpoint checkpoint = progress.activeCheckpoint().orElseThrow();
        assertEquals("budget-question-change", checkpoint.changeName());
        assertEquals(List.of(PipelinePhase.RECOMMEND), checkpoint.completedPhases());
        assertFalse(exhaustedEvents.isEmpty());
        assertEquals(850L, exhaustedEvents.get(0).metadata().get("tokenUsage"));
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
        waitUntilState(driver, LoopState.STOPPED);

        assertEquals(LoopState.STOPPED, driver.getState());
        assertEquals(2, driver.getCompletedIterations().size());
        assertTrue(exhaustedEvents.isEmpty()); // no exhaustion event
    }

    // -------------------------------------------------------------------------
    // LoopAnswerAgent integration tests
    // -------------------------------------------------------------------------

    private static Question sampleQuestion() {
        return question("q-test", "session-test",
                "What should I do?", "Critical", "Use approach A",
                QuestionCategory.CLARIFICATION, DeliveryMode.AUTO_AI_REPLY);
    }

    private static Question question(String questionId, String sessionId,
                                     String text, String impact, String recommendation,
                                     QuestionCategory category, DeliveryMode deliveryMode) {
        return new Question(questionId, sessionId, text, impact, recommendation,
                QuestionStatus.WAITING_FOR_ANSWER, category, deliveryMode);
    }

    private static Answer sampleAnswer() {
        return new Answer("Use approach A", "AI analysis", "LoopAnswerAgent",
                AnswerSource.AI_AGENT, 0.8, QuestionDecision.ANSWER_ACCEPTED,
                DeliveryMode.AUTO_AI_REPLY, null, System.currentTimeMillis());
    }

    /**
     * Pipeline that returns QUESTIONING on the first call,
     * then SUCCESS on subsequent calls (for retry path).
     */
    private static class QuestioningThenSuccessPipeline implements LoopPipeline {
        private final AtomicInteger callCount = new AtomicInteger();
        private final Question question;

        QuestioningThenSuccessPipeline(Question question) {
            this.question = question;
        }

        @Override
        public IterationResult execute(LoopCandidate candidate, LoopConfig config, Set<PipelinePhase> skipPhases) {
            if (callCount.incrementAndGet() == 1) {
                return new IterationResult(IterationStatus.QUESTIONING, null, 10,
                        List.of(PipelinePhase.RECOMMEND, PipelinePhase.PROPOSE), 0, question);
            }
            return new IterationResult(IterationStatus.SUCCESS, null, 10,
                    List.of(PipelinePhase.IMPLEMENT, PipelinePhase.VERIFY,
                            PipelinePhase.REVIEW, PipelinePhase.ARCHIVE));
        }
    }

    private static class TokenQuestioningThenSuccessPipeline implements LoopPipeline {
        private final AtomicInteger callCount = new AtomicInteger();
        private final Question question;
        private final long initialTokenUsage;
        private final long resumedTokenUsage;

        TokenQuestioningThenSuccessPipeline(Question question,
                                            long initialTokenUsage,
                                            long resumedTokenUsage) {
            this.question = question;
            this.initialTokenUsage = initialTokenUsage;
            this.resumedTokenUsage = resumedTokenUsage;
        }

        @Override
        public IterationResult execute(LoopCandidate candidate, LoopConfig config, Set<PipelinePhase> skipPhases) {
            if (callCount.incrementAndGet() == 1) {
                return new IterationResult(IterationStatus.QUESTIONING, null, 10,
                        List.of(PipelinePhase.RECOMMEND, PipelinePhase.PROPOSE), initialTokenUsage, question);
            }
            return new IterationResult(IterationStatus.SUCCESS, null, 10,
                    List.of(PipelinePhase.IMPLEMENT, PipelinePhase.VERIFY,
                            PipelinePhase.REVIEW, PipelinePhase.ARCHIVE),
                    resumedTokenUsage);
        }
    }

    @Test
    void questioningWithResolvedAnswerAgentResumesAndSucceeds() throws Exception {
        SimpleEventBus bus = eventBus();
        List<Event> routedEvents = new ArrayList<>();
        List<Event> answeredEvents = new ArrayList<>();
        bus.subscribe(EventType.LOOP_QUESTION_ROUTED, routedEvents::add);
        bus.subscribe(EventType.LOOP_QUESTION_ANSWERED, answeredEvents::add);

        Question question = sampleQuestion();
        Answer answer = sampleAnswer();

        LoopAnswerAgent agentStub = (q, timeout) -> new AnswerResolution.Resolved(answer);
        QuestioningThenSuccessPipeline pipeline = new QuestioningThenSuccessPipeline(question);

        LoopConfig cfg = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus);
        LoopScheduler scheduler = ctx ->
                Optional.of(new LoopCandidate("my-change", "m.md", "goal"));

        DefaultLoopDriver driver = new DefaultLoopDriver(cfg, scheduler, pipeline, null, agentStub);
        driver.start();
        waitUntilState(driver, LoopState.STOPPED);

        assertEquals(LoopState.STOPPED, driver.getState());
        // Iteration completed with SUCCESS (from retry)
        assertEquals(1, driver.getCompletedIterations().size());
        assertEquals(IterationStatus.SUCCESS, driver.getCompletedIterations().get(0).status());

        // Events published
        assertFalse(routedEvents.isEmpty(), "LOOP_QUESTION_ROUTED should be published");
        assertFalse(answeredEvents.isEmpty(), "LOOP_QUESTION_ANSWERED should be published");
        assertEquals("q-test", routedEvents.get(0).metadata().get("questionId"));
    }

    @Test
    void questioningWithEscalatedAnswerAgentPausesLoop() throws Exception {
        SimpleEventBus bus = eventBus();
        List<Event> escalatedEvents = new ArrayList<>();
        List<Event> pausedEvents = new ArrayList<>();
        bus.subscribe(EventType.LOOP_QUESTION_ESCALATED, escalatedEvents::add);
        bus.subscribe(EventType.LOOP_PAUSED, pausedEvents::add);

        Question question = sampleQuestion();
        LoopAnswerAgent agentStub = (q, timeout) -> new AnswerResolution.Escalated("too complex");

        QuestioningThenSuccessPipeline pipeline = new QuestioningThenSuccessPipeline(question);
        LoopConfig cfg = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus);
        LoopScheduler scheduler = ctx ->
                Optional.of(new LoopCandidate("my-change", "m.md", "goal"));

        DefaultLoopDriver driver = new DefaultLoopDriver(cfg, scheduler, pipeline, null, agentStub);
        driver.start();
        waitUntilState(driver, LoopState.PAUSED);

        assertEquals(LoopState.PAUSED, driver.getState());
        assertFalse(escalatedEvents.isEmpty(), "LOOP_QUESTION_ESCALATED should be published");
        assertEquals("too complex", escalatedEvents.get(0).metadata().get("reason"));
        // Partial iteration recorded
        assertEquals(1, driver.getCompletedIterations().size());
        assertEquals(IterationStatus.QUESTIONING, driver.getCompletedIterations().get(0).status());
        assertTrue(driver.getCompletedIterations().get(0).failureReason().contains("too complex"));

        driver.stop();
    }

    @Test
    void questioningPausePersistsCumulativeTokenUsage() throws Exception {
        SimpleEventBus bus = eventBus();
        LealoneLoopIterationStore store = createStore(bus);

        Question question = sampleQuestion();
        TokenQuestioningThenSuccessPipeline pipeline = new TokenQuestioningThenSuccessPipeline(question, 250, 0);
        ContextBudget budget = ContextBudget.of(5000, 20);
        LoopConfig cfg = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus, budget);
        LoopScheduler scheduler = ctx ->
                Optional.of(new LoopCandidate("question-pause-change", "m.md", "goal"));

        DefaultLoopDriver driver = new DefaultLoopDriver(cfg, scheduler, pipeline, store, null);
        driver.start();

        waitUntilState(driver, LoopState.PAUSED);

        LoopProgress progress = store.loadProgress().orElseThrow();
        assertEquals(LoopState.PAUSED, progress.loopState());
        assertEquals(250, progress.tokenUsage());

        driver.stop();
    }

    @Test
    void questioningWithNullAnswerAgentPausesWithConfiguredReason() throws Exception {
        SimpleEventBus bus = eventBus();
        List<Event> escalatedEvents = new ArrayList<>();
        bus.subscribe(EventType.LOOP_QUESTION_ESCALATED, escalatedEvents::add);

        Question question = sampleQuestion();
        QuestioningThenSuccessPipeline pipeline = new QuestioningThenSuccessPipeline(question);
        LoopConfig cfg = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus);
        LoopScheduler scheduler = ctx ->
                Optional.of(new LoopCandidate("my-change", "m.md", "goal"));

        // No answer agent — pass null
        DefaultLoopDriver driver = new DefaultLoopDriver(cfg, scheduler, pipeline, null, null);
        driver.start();
        waitUntilState(driver, LoopState.PAUSED);

        assertEquals(LoopState.PAUSED, driver.getState());
        assertEquals(1, driver.getCompletedIterations().size());
        assertEquals(IterationStatus.QUESTIONING, driver.getCompletedIterations().get(0).status());
        assertTrue(driver.getCompletedIterations().get(0).failureReason()
                .contains("no answer agent configured"));
        assertFalse(escalatedEvents.isEmpty());

        driver.stop();
    }

    @Test
    void loopQuestionRoutedEventContainsRequiredMetadata() throws Exception {
        SimpleEventBus bus = eventBus();
        List<Event> routedEvents = new ArrayList<>();
        bus.subscribe(EventType.LOOP_QUESTION_ROUTED, routedEvents::add);

        Question question = sampleQuestion();
        Answer answer = sampleAnswer();
        LoopAnswerAgent agentStub = (q, timeout) -> new AnswerResolution.Resolved(answer);
        QuestioningThenSuccessPipeline pipeline = new QuestioningThenSuccessPipeline(question);

        LoopConfig cfg = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus);
        LoopScheduler scheduler = ctx ->
                Optional.of(new LoopCandidate("routed-change", "m.md", "goal"));

        DefaultLoopDriver driver = new DefaultLoopDriver(cfg, scheduler, pipeline, null, agentStub);
        driver.start();
        waitUntilNonEmpty(routedEvents);

        assertFalse(routedEvents.isEmpty());
        Event routed = routedEvents.get(0);
        assertEquals("q-test", routed.metadata().get("questionId"));
        assertEquals("routed-change", routed.metadata().get("changeName"));
        assertEquals("session-test", routed.metadata().get("sessionId"));

        driver.stop();
    }

    @Test
    void loopQuestionAnsweredEventContainsConfidence() throws Exception {
        SimpleEventBus bus = eventBus();
        List<Event> answeredEvents = new ArrayList<>();
        bus.subscribe(EventType.LOOP_QUESTION_ANSWERED, answeredEvents::add);

        Question question = sampleQuestion();
        Answer answer = sampleAnswer();
        LoopAnswerAgent agentStub = (q, timeout) -> new AnswerResolution.Resolved(answer);
        QuestioningThenSuccessPipeline pipeline = new QuestioningThenSuccessPipeline(question);

        LoopConfig cfg = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus);
        LoopScheduler scheduler = ctx ->
                Optional.of(new LoopCandidate("answered-change", "m.md", "goal"));

        DefaultLoopDriver driver = new DefaultLoopDriver(cfg, scheduler, pipeline, null, agentStub);
        driver.start();
        waitUntilNonEmpty(answeredEvents);

        assertFalse(answeredEvents.isEmpty());
        Event answered = answeredEvents.get(0);
        assertEquals("q-test", answered.metadata().get("questionId"));
        assertEquals(0.8, (double) answered.metadata().get("confidence"), 0.001);

        driver.stop();
    }

    @Test
    void resumedQuestioningIterationAccumulatesTokenUsageAcrossBothPipelineRuns() throws Exception {
        SimpleEventBus bus = eventBus();
        LealoneLoopIterationStore store = createStore(bus);

        Question question = sampleQuestion();
        Answer answer = sampleAnswer();
        LoopAnswerAgent agentStub = (q, timeout) -> new AnswerResolution.Resolved(answer);
        TokenQuestioningThenSuccessPipeline pipeline = new TokenQuestioningThenSuccessPipeline(question, 150, 250);
        ContextBudget budget = ContextBudget.of(5000, 20);
        LoopConfig cfg = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus, budget);
        LoopScheduler scheduler = ctx ->
                Optional.of(new LoopCandidate("resume-token-change", "m.md", "goal"));

        DefaultLoopDriver driver = new DefaultLoopDriver(cfg, scheduler, pipeline, store, agentStub);
        driver.start();

        waitUntilState(driver, LoopState.STOPPED);

        LoopProgress progress = store.loadProgress().orElseThrow();
        assertEquals(LoopState.STOPPED, progress.loopState());
        assertEquals(400, progress.tokenUsage());
    }

    @Test
    void humanOnlyCategoriesBypassAnswerAgentAndPauseLoop() throws Exception {
        assertHumanOnlyCategoryBypassesAnswerAgent(QuestionCategory.PERMISSION_CONFIRMATION);
        assertHumanOnlyCategoryBypassesAnswerAgent(QuestionCategory.IRREVERSIBLE_APPROVAL);
    }

    private void assertHumanOnlyCategoryBypassesAnswerAgent(QuestionCategory category) throws Exception {
        SimpleEventBus bus = eventBus();
        List<Event> escalatedEvents = new ArrayList<>();
        bus.subscribe(EventType.LOOP_QUESTION_ESCALATED, escalatedEvents::add);

        Question question = question("q-" + category.name(), "session-" + category.name(),
                "May the loop continue?", "Requires operator approval.",
                "Ask a human before proceeding.", category, DeliveryMode.PAUSE_WAIT_HUMAN);
        QuestioningThenSuccessPipeline pipeline = new QuestioningThenSuccessPipeline(question);
        AtomicInteger answerAgentCalls = new AtomicInteger();
        LoopAnswerAgent agent = (q, timeout) -> {
            answerAgentCalls.incrementAndGet();
            return new AnswerResolution.Resolved(sampleAnswer());
        };

        LoopConfig cfg = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus);
        LoopScheduler scheduler = ctx ->
                Optional.of(new LoopCandidate("human-only-change", "m.md", "goal"));
        DefaultLoopDriver driver = new DefaultLoopDriver(cfg, scheduler, pipeline, null, agent);
        driver.start();
        waitUntilState(driver, LoopState.PAUSED);

        assertEquals(LoopState.PAUSED, driver.getState());
        assertEquals(0, answerAgentCalls.get());
        assertEquals(1, driver.getCompletedIterations().size());
        assertEquals(IterationStatus.QUESTIONING, driver.getCompletedIterations().get(0).status());
        assertFalse(escalatedEvents.isEmpty());
        assertTrue(driver.getCompletedIterations().get(0).failureReason().contains("requires human approval"));

        driver.stop();
    }

    @Test
    void humanDeliveryModesBypassAnswerAgentAndExposeEscalationMetadata() throws Exception {
        assertHumanDeliveryModeBypassesAnswerAgent(DeliveryMode.PAUSE_WAIT_HUMAN);
        assertHumanDeliveryModeBypassesAnswerAgent(DeliveryMode.PUSH_MOBILE_WAIT_HUMAN);
    }

    private void assertHumanDeliveryModeBypassesAnswerAgent(DeliveryMode deliveryMode) throws Exception {
        SimpleEventBus bus = eventBus();
        List<Event> escalatedEvents = new ArrayList<>();
        bus.subscribe(EventType.LOOP_QUESTION_ESCALATED, escalatedEvents::add);

        Question question = question("q-" + deliveryMode.name(), "session-" + deliveryMode.name(),
                "Need outside input?", "Configured delivery requires a person.",
                "Wait for human handling.", QuestionCategory.PLAN_SELECTION, deliveryMode);
        QuestioningThenSuccessPipeline pipeline = new QuestioningThenSuccessPipeline(question);
        AtomicInteger answerAgentCalls = new AtomicInteger();
        LoopAnswerAgent agent = (q, timeout) -> {
            answerAgentCalls.incrementAndGet();
            return new AnswerResolution.Resolved(sampleAnswer());
        };

        LoopConfig cfg = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus);
        LoopScheduler scheduler = ctx ->
                Optional.of(new LoopCandidate("human-delivery-change", "m.md", "goal"));
        DefaultLoopDriver driver = new DefaultLoopDriver(cfg, scheduler, pipeline, null, agent);
        driver.start();
        waitUntilState(driver, LoopState.PAUSED);

        assertEquals(LoopState.PAUSED, driver.getState());
        assertEquals(0, answerAgentCalls.get());
        assertFalse(escalatedEvents.isEmpty());
        Event event = escalatedEvents.get(0);
        assertEquals(question.questionId(), event.metadata().get("questionId"));
        assertEquals(question.sessionId(), event.metadata().get("sessionId"));
        assertEquals("human-delivery-change", event.metadata().get("changeName"));
        assertEquals(question.category().name(), event.metadata().get("category"));
        assertEquals(deliveryMode.name(), event.metadata().get("deliveryMode"));
        assertTrue(((String) event.metadata().get("reason")).contains("requires human handling"));
        assertNotNull(event.metadata().get("routingReason"));

        driver.stop();
    }

    @Test
    void escalatedPartialIterationDoesNotMarkChangeCompleteInStore() throws Exception {
        SimpleEventBus bus = eventBus();
        LealoneLoopIterationStore store = createStore(bus);

        Question question = question("q-persist", "session-persist",
                "Can this change proceed?", "The change requires approval.",
                "Pause for a human.", QuestionCategory.PERMISSION_CONFIRMATION,
                DeliveryMode.PAUSE_WAIT_HUMAN);
        QuestioningThenSuccessPipeline pipeline = new QuestioningThenSuccessPipeline(question);
        LoopConfig cfg = new LoopConfig(2, 60, List.of(), Path.of("/tmp"), bus);
        LoopScheduler scheduler = ctx ->
                Optional.of(new LoopCandidate("approval-change", "m.md", "goal"));
        DefaultLoopDriver driver = new DefaultLoopDriver(cfg, scheduler, pipeline, store, (q, timeout) -> {
            fail("LoopAnswerAgent must not be invoked for human-only questions");
            return new AnswerResolution.Escalated("unexpected");
        });
        driver.start();
        waitUntilState(driver, LoopState.PAUSED);

        assertEquals(LoopState.PAUSED, driver.getState());
        LoopProgress progress = store.loadProgress().orElseThrow();
        assertEquals(LoopState.PAUSED, progress.loopState());
        assertFalse(progress.completedChangeNames().contains("approval-change"));
        LoopPhaseCheckpoint checkpoint = progress.activeCheckpoint().orElseThrow();
        assertEquals("approval-change", checkpoint.changeName());
        assertEquals(List.of(PipelinePhase.RECOMMEND, PipelinePhase.PROPOSE),
                checkpoint.completedPhases());
        assertEquals(1, store.loadIterations().size());
        assertEquals(IterationStatus.QUESTIONING, store.loadIterations().get(0).status());

        driver.stop();
    }

    @Test
    void recoveredEscalatedProgressAllowsSameChangeToRunAgain() throws Exception {
        SimpleEventBus bus = eventBus();
        LealoneLoopIterationStore store = createStore(bus);
        store.saveIteration(new LoopIteration(1, "approval-change", "m.md",
                100L, 200L, IterationStatus.QUESTIONING, "requires human approval"));
        store.saveProgress(new LoopProgress(LoopState.PAUSED, Set.of(), 1));

        CountDownLatch schedulerCalled = new CountDownLatch(1);
        LoopConfig cfg = new LoopConfig(2, 60, List.of(), Path.of("/tmp"), bus);
        LoopScheduler scheduler = ctx -> {
            assertFalse(ctx.completedChangeNames().contains("approval-change"));
            schedulerCalled.countDown();
            return Optional.of(new LoopCandidate("approval-change", "m.md", "goal"));
        };

        DefaultLoopDriver driver = new DefaultLoopDriver(cfg, scheduler, new StubLoopPipeline(), store);
        driver.start();

        assertTrue(schedulerCalled.await(5, TimeUnit.SECONDS));
        waitUntilState(driver, LoopState.STOPPED);

        assertEquals(LoopState.STOPPED, driver.getState());
        assertEquals(2, driver.getCompletedIterations().size());
        assertEquals(IterationStatus.SUCCESS, driver.getCompletedIterations().get(1).status());
        assertTrue(store.loadProgress().orElseThrow().completedChangeNames().contains("approval-change"));
    }

    @Test
    void resumeAfterHumanEscalationRetriesPausedChange() throws Exception {
        SimpleEventBus bus = eventBus();
        Question question = question("q-resume", "session-resume",
                "Can this change proceed?", "The change requires approval.",
                "Pause for a human.", QuestionCategory.PERMISSION_CONFIRMATION,
                DeliveryMode.PAUSE_WAIT_HUMAN);
        QuestioningThenSuccessPipeline pipeline = new QuestioningThenSuccessPipeline(question);
        LoopConfig cfg = new LoopConfig(2, 60, List.of(), Path.of("/tmp"), bus);
        LoopScheduler scheduler = ctx ->
                Optional.of(new LoopCandidate("approval-change", "m.md", "goal"));

        DefaultLoopDriver driver = new DefaultLoopDriver(cfg, scheduler, pipeline, null, (q, timeout) -> {
            fail("LoopAnswerAgent must not be invoked for human-only questions");
            return new AnswerResolution.Escalated("unexpected");
        });
        driver.start();

        waitUntilState(driver, LoopState.PAUSED);
        driver.resume();
        waitUntilState(driver, LoopState.STOPPED);

        assertEquals(LoopState.STOPPED, driver.getState());
        assertEquals(2, driver.getCompletedIterations().size());
        assertEquals(IterationStatus.QUESTIONING, driver.getCompletedIterations().get(0).status());
        assertEquals(IterationStatus.SUCCESS, driver.getCompletedIterations().get(1).status());
    }

    @Test
    void configuredDeliveryServiceReceivesHumanEscalationQuestion() throws Exception {
        SimpleEventBus bus = eventBus();
        RecordingQuestionChannel channel = new RecordingQuestionChannel();
        QuestionRuntime runtime = new QuestionRuntime(bus);
        InMemoryQuestionStore store = new InMemoryQuestionStore();
        runtime.setQuestionStore(store);
        QuestionReplyCollector collector = new InMemoryReplyCollector(runtime);
        QuestionDeliveryService deliveryService = new QuestionDeliveryService(channel, collector, runtime, store);

        Question question = question("q-delivery", "session-delivery",
                "Need mobile confirmation?", "Configured channel must receive the question.",
                "Notify a human.", QuestionCategory.PLAN_SELECTION,
                DeliveryMode.PUSH_MOBILE_WAIT_HUMAN);
        QuestioningThenSuccessPipeline pipeline = new QuestioningThenSuccessPipeline(question);
        LoopConfig cfg = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus);
        LoopScheduler scheduler = ctx ->
                Optional.of(new LoopCandidate("delivery-change", "m.md", "goal"));

        DefaultLoopDriver driver = new DefaultLoopDriver(cfg, scheduler, pipeline, null,
                (q, timeout) -> {
                    fail("LoopAnswerAgent must not be invoked for human delivery modes");
                    return new AnswerResolution.Escalated("unexpected");
                },
                deliveryService);
        driver.start();
        waitUntilState(driver, LoopState.PAUSED);

        assertEquals(LoopState.PAUSED, driver.getState());
        assertEquals(1, channel.sent.size());
        assertEquals("q-delivery", channel.sent.get(0).questionId());
        assertTrue(deliveryService.pendingQuestion("session-delivery").isPresent());

        driver.stop();
    }

    private static void waitUntilState(LoopDriver driver, LoopState expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (driver.getState() == expected) return;
            Thread.sleep(25);
        }
        fail("Expected state " + expected + " but got " + driver.getState());
    }

    private static void waitUntilStateIn(LoopDriver driver, LoopState... expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            LoopState current = driver.getState();
            for (LoopState s : expected) {
                if (current == s) return;
            }
            Thread.sleep(25);
        }
        fail("Expected one of " + java.util.Arrays.toString(expected) + " but got " + driver.getState());
    }

    private static void waitUntilNonEmpty(List<?> list) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            synchronized (list) {
                if (!list.isEmpty()) return;
            }
            Thread.sleep(25);
        }
        fail("Expected list to be non-empty within 5s");
    }

    private static <T> T waitUntilReference(AtomicReference<T> reference) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            T value = reference.get();
            if (value != null) return value;
            Thread.sleep(25);
        }
        fail("Expected reference to be set");
        return null;
    }

    private static class RecordingQuestionChannel implements QuestionDeliveryChannel {
        private final List<Question> sent = new ArrayList<>();

        @Override
        public void send(Question question) {
            sent.add(question);
        }

        @Override
        public void close() {
        }
    }

    private static class InMemoryQuestionStore implements QuestionStore {
        private final Map<String, Question> questions = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public String save(Question question) {
            questions.put(question.questionId(), question);
            return question.questionId();
        }

        @Override
        public Question update(String questionId, QuestionStatus status) {
            Question existing = questions.get(questionId);
            if (existing == null) {
                throw new IllegalStateException("missing question: " + questionId);
            }
            Question updated = new Question(
                    existing.questionId(),
                    existing.sessionId(),
                    existing.question(),
                    existing.impact(),
                    existing.recommendation(),
                    status,
                    existing.category(),
                    existing.deliveryMode()
            );
            questions.put(questionId, updated);
            return updated;
        }

        @Override
        public List<Question> findBySession(String sessionId) {
            return questions.values().stream()
                    .filter(question -> question.sessionId().equals(sessionId))
                    .toList();
        }

        @Override
        public List<Question> findByStatus(QuestionStatus status) {
            return questions.values().stream()
                    .filter(question -> question.status() == status)
                    .toList();
        }

        @Override
        public Optional<Question> findPending(String sessionId) {
            return questions.values().stream()
                    .filter(question -> question.sessionId().equals(sessionId))
                    .filter(question -> question.status() == QuestionStatus.WAITING_FOR_ANSWER)
                    .findFirst();
        }

        @Override
        public void delete(String questionId) {
            questions.remove(questionId);
        }
    }

    // -------------------------------------------------------------------------
    // Interactive session bridge tests
    // -------------------------------------------------------------------------

    /**
     * Controllable InteractiveSession for testing.
     */
    private static class StubInteractiveSession implements InteractiveSession {
        private final String sessionId;
        private InteractiveSessionState state = InteractiveSessionState.NEW;
        private final List<String> inputs = new ArrayList<>();

        StubInteractiveSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override public String sessionId() { return sessionId; }
        @Override public InteractiveSessionState state() { return state; }

        @Override
        public void start() {
            if (state != InteractiveSessionState.NEW) {
                throw new IllegalStateException("Cannot start from " + state);
            }
            state = InteractiveSessionState.ACTIVE;
        }

        @Override
        public void submit(String input) {
            if (state != InteractiveSessionState.ACTIVE) {
                throw new IllegalStateException("Cannot submit from " + state);
            }
            inputs.add(input);
        }

        @Override
        public List<String> drainOutput() { return List.of(); }

        @Override
        public void close() {
            if (state == InteractiveSessionState.CLOSED) return;
            state = InteractiveSessionState.CLOSED;
        }

        void transitionToError() {
            state = InteractiveSessionState.ERROR;
        }
    }

    /**
     * Human-escalated question that triggers pause (no factory configured).
     */
    private static Question humanEscalatedQuestion(String questionId, String sessionId) {
        return question(questionId, sessionId,
                "Need approval?", "Requires human.", "Ask operator.",
                QuestionCategory.PERMISSION_CONFIRMATION, DeliveryMode.PAUSE_WAIT_HUMAN);
    }

    @Test
    void noFactoryConfiguredPauseBehaviorIdenticalToPreChange() throws Exception {
        SimpleEventBus bus = eventBus();
        List<Event> enteredEvents = new ArrayList<>();
        bus.subscribe(EventType.LOOP_INTERACTIVE_ENTERED, enteredEvents::add);

        Question question = humanEscalatedQuestion("q-nofactory", "s-nofactory");
        QuestioningThenSuccessPipeline pipeline = new QuestioningThenSuccessPipeline(question);
        LoopConfig cfg = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus);
        LoopScheduler scheduler = ctx ->
                Optional.of(new LoopCandidate("no-factory-change", "m.md", "goal"));

        // No factory — using 5-arg constructor (no factory)
        DefaultLoopDriver driver = new DefaultLoopDriver(cfg, scheduler, pipeline, null,
                (q, timeout) -> new AnswerResolution.Escalated("escalated"));
        driver.start();
        waitUntilState(driver, LoopState.PAUSED);

        assertEquals(LoopState.PAUSED, driver.getState());
        assertTrue(enteredEvents.isEmpty(), "No LOOP_INTERACTIVE_ENTERED without factory");
        assertEquals(1, driver.getCompletedIterations().size());
        assertEquals(IterationStatus.QUESTIONING, driver.getCompletedIterations().get(0).status());

        driver.stop();
    }

    @Test
    void factoryConfiguredCreatesInteractiveSessionOnHumanEscalation() throws Exception {
        SimpleEventBus bus = eventBus();
        List<Event> enteredEvents = new ArrayList<>();
        bus.subscribe(EventType.LOOP_INTERACTIVE_ENTERED, enteredEvents::add);

        Question question = humanEscalatedQuestion("q-factory", "s-factory");
        QuestioningThenSuccessPipeline pipeline = new QuestioningThenSuccessPipeline(question);
        LoopConfig cfg = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus);
        LoopScheduler scheduler = ctx ->
                Optional.of(new LoopCandidate("factory-change", "m.md", "goal"));

        AtomicReference<StubInteractiveSession> createdSession = new AtomicReference<>();
        InteractiveSessionFactory factory = sessionId -> {
            StubInteractiveSession session = new StubInteractiveSession(sessionId);
            createdSession.set(session);
            return session;
        };

        DefaultLoopDriver driver = new DefaultLoopDriver(cfg, scheduler, pipeline, null,
                (q, timeout) -> new AnswerResolution.Escalated("escalated"),
                null, factory);
        driver.start();
        waitUntilState(driver, LoopState.PAUSED);

        assertEquals(LoopState.PAUSED, driver.getState());
        StubInteractiveSession session = waitUntilReference(createdSession);
        assertEquals(InteractiveSessionState.ACTIVE, session.state());
        assertFalse(enteredEvents.isEmpty(), "LOOP_INTERACTIVE_ENTERED should be published");
        assertEquals("s-factory", enteredEvents.get(0).metadata().get("sessionId"));
        assertEquals("q-factory", enteredEvents.get(0).metadata().get("questionId"));
        assertEquals("factory-change", enteredEvents.get(0).metadata().get("changeName"));

        // Close session to unblock
        session.close();
        driver.stop();
    }

    @Test
    void sessionClosedLoopRemainsPausedExitedEventPublished() throws Exception {
        SimpleEventBus bus = eventBus();
        List<Event> exitedEvents = new ArrayList<>();
        bus.subscribe(EventType.LOOP_INTERACTIVE_EXITED, exitedEvents::add);

        Question question = humanEscalatedQuestion("q-close", "s-close");
        QuestioningThenSuccessPipeline pipeline = new QuestioningThenSuccessPipeline(question);
        LoopConfig cfg = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus);
        LoopScheduler scheduler = ctx ->
                Optional.of(new LoopCandidate("close-change", "m.md", "goal"));

        AtomicReference<StubInteractiveSession> createdSession = new AtomicReference<>();
        InteractiveSessionFactory factory = sessionId -> {
            StubInteractiveSession session = new StubInteractiveSession(sessionId);
            createdSession.set(session);
            return session;
        };

        DefaultLoopDriver driver = new DefaultLoopDriver(cfg, scheduler, pipeline, null,
                (q, timeout) -> new AnswerResolution.Escalated("escalated"),
                null, factory);
        driver.start();

        waitUntilState(driver, LoopState.PAUSED);
        StubInteractiveSession session = waitUntilReference(createdSession);

        // Close the session — should publish EXITED but loop stays PAUSED
        session.close();
        waitUntilNonEmpty(exitedEvents);

        assertEquals(LoopState.PAUSED, driver.getState());
        assertFalse(exitedEvents.isEmpty(), "LOOP_INTERACTIVE_EXITED should be published");
        assertEquals("CLOSED", exitedEvents.get(0).metadata().get("sessionEndState"));

        driver.stop();
    }

    @Test
    void sessionErrorLoopRemainsPausedExitedEventPublished() throws Exception {
        SimpleEventBus bus = eventBus();
        List<Event> exitedEvents = new ArrayList<>();
        bus.subscribe(EventType.LOOP_INTERACTIVE_EXITED, exitedEvents::add);

        Question question = humanEscalatedQuestion("q-error", "s-error");
        QuestioningThenSuccessPipeline pipeline = new QuestioningThenSuccessPipeline(question);
        LoopConfig cfg = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus);
        LoopScheduler scheduler = ctx ->
                Optional.of(new LoopCandidate("error-change", "m.md", "goal"));

        AtomicReference<StubInteractiveSession> createdSession = new AtomicReference<>();
        InteractiveSessionFactory factory = sessionId -> {
            StubInteractiveSession session = new StubInteractiveSession(sessionId);
            createdSession.set(session);
            return session;
        };

        DefaultLoopDriver driver = new DefaultLoopDriver(cfg, scheduler, pipeline, null,
                (q, timeout) -> new AnswerResolution.Escalated("escalated"),
                null, factory);
        driver.start();

        waitUntilState(driver, LoopState.PAUSED);
        StubInteractiveSession session = waitUntilReference(createdSession);

        // Transition session to ERROR
        session.transitionToError();
        waitUntilNonEmpty(exitedEvents);

        assertEquals(LoopState.PAUSED, driver.getState());
        assertFalse(exitedEvents.isEmpty(), "LOOP_INTERACTIVE_EXITED should be published");
        assertEquals("ERROR", exitedEvents.get(0).metadata().get("sessionEndState"));

        driver.stop();
    }

    @Test
    void stopDuringInteractiveSessionClosesSessionAndStopsLoop() throws Exception {
        SimpleEventBus bus = eventBus();
        List<Event> exitedEvents = new ArrayList<>();
        bus.subscribe(EventType.LOOP_INTERACTIVE_EXITED, exitedEvents::add);

        Question question = humanEscalatedQuestion("q-stop", "s-stop");
        QuestioningThenSuccessPipeline pipeline = new QuestioningThenSuccessPipeline(question);
        LoopConfig cfg = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus);
        LoopScheduler scheduler = ctx ->
                Optional.of(new LoopCandidate("stop-change", "m.md", "goal"));

        AtomicReference<StubInteractiveSession> createdSession = new AtomicReference<>();
        InteractiveSessionFactory factory = sessionId -> {
            StubInteractiveSession session = new StubInteractiveSession(sessionId);
            createdSession.set(session);
            return session;
        };

        DefaultLoopDriver driver = new DefaultLoopDriver(cfg, scheduler, pipeline, null,
                (q, timeout) -> new AnswerResolution.Escalated("escalated"),
                null, factory);
        driver.start();

        waitUntilState(driver, LoopState.PAUSED);
        StubInteractiveSession session = waitUntilReference(createdSession);
        assertEquals(InteractiveSessionState.ACTIVE, session.state());

        driver.stop();
        waitUntilState(driver, LoopState.STOPPED);
        waitUntilNonEmpty(exitedEvents);

        assertEquals(LoopState.STOPPED, driver.getState());
        assertEquals(InteractiveSessionState.CLOSED, session.state());
        assertFalse(exitedEvents.isEmpty(), "LOOP_INTERACTIVE_EXITED should be published on stop");
    }

    @Test
    void multiplePausesEachCreatesFreshSessionInstance() throws Exception {
        SimpleEventBus bus = eventBus();
        List<Event> enteredEvents = new ArrayList<>();
        bus.subscribe(EventType.LOOP_INTERACTIVE_ENTERED, enteredEvents::add);

        // Question triggers on first call, then SUCCESS, then question again on 3rd
        AtomicInteger pipelineCallCount = new AtomicInteger();
        Question question1 = humanEscalatedQuestion("q-multi1", "s-multi");
        Question question2 = humanEscalatedQuestion("q-multi2", "s-multi");
        LoopPipeline pipeline = (candidate, config, skipPhases) -> {
            int call = pipelineCallCount.incrementAndGet();
            if (call == 1) {
                return new IterationResult(IterationStatus.QUESTIONING, null, 10,
                        List.of(PipelinePhase.RECOMMEND, PipelinePhase.PROPOSE), 0, question1);
            } else if (call == 3) {
                return new IterationResult(IterationStatus.QUESTIONING, null, 10,
                        List.of(PipelinePhase.RECOMMEND, PipelinePhase.PROPOSE), 0, question2);
            }
            return new IterationResult(IterationStatus.SUCCESS, null, 10,
                    List.of(PipelinePhase.IMPLEMENT, PipelinePhase.VERIFY,
                            PipelinePhase.REVIEW, PipelinePhase.ARCHIVE));
        };

        LoopConfig cfg = new LoopConfig(4, 60, List.of(), Path.of("/tmp"), bus);
        AtomicInteger schedulerCallCount = new AtomicInteger();
        LoopScheduler scheduler = ctx -> {
            int call = schedulerCallCount.incrementAndGet();
            return Optional.of(new LoopCandidate("multi-change", "m.md", "goal"));
        };

        List<StubInteractiveSession> allSessions = new ArrayList<>();
        InteractiveSessionFactory factory = sessionId -> {
            StubInteractiveSession session = new StubInteractiveSession(sessionId);
            synchronized (allSessions) { allSessions.add(session); }
            return session;
        };

        DefaultLoopDriver driver = new DefaultLoopDriver(cfg, scheduler, pipeline, null,
                (q, timeout) -> new AnswerResolution.Escalated("escalated"),
                null, factory);
        driver.start();

        // Wait for first pause
        waitUntilState(driver, LoopState.PAUSED);
        assertEquals(1, allSessions.size());
        StubInteractiveSession first = allSessions.get(0);

        // Close first session and resume
        first.close();
        waitUntilStateIn(driver, LoopState.PAUSED, LoopState.RECOMMENDING);
        driver.resume();

        // Wait for second pause
        waitUntilState(driver, LoopState.PAUSED);
        assertEquals(2, allSessions.size());
        assertNotSame(first, allSessions.get(1), "Second pause must create fresh session");

        allSessions.get(1).close();
        driver.stop();
    }

    @Test
    void factoryReturnsSessionInNewState() {
        StubInteractiveSession session = new StubInteractiveSession("test-session");
        assertEquals(InteractiveSessionState.NEW, session.state());
    }

    @Test
    void interactiveEnteredExitedEventsContainCorrectMetadata() throws Exception {
        SimpleEventBus bus = eventBus();
        List<Event> enteredEvents = new ArrayList<>();
        List<Event> exitedEvents = new ArrayList<>();
        bus.subscribe(EventType.LOOP_INTERACTIVE_ENTERED, enteredEvents::add);
        bus.subscribe(EventType.LOOP_INTERACTIVE_EXITED, exitedEvents::add);

        Question question = humanEscalatedQuestion("q-meta", "s-meta");
        QuestioningThenSuccessPipeline pipeline = new QuestioningThenSuccessPipeline(question);
        LoopConfig cfg = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus);
        LoopScheduler scheduler = ctx ->
                Optional.of(new LoopCandidate("meta-change", "m.md", "goal"));

        AtomicReference<StubInteractiveSession> createdSession = new AtomicReference<>();
        InteractiveSessionFactory factory = sessionId -> {
            StubInteractiveSession session = new StubInteractiveSession(sessionId);
            createdSession.set(session);
            return session;
        };

        DefaultLoopDriver driver = new DefaultLoopDriver(cfg, scheduler, pipeline, null,
                (q, timeout) -> new AnswerResolution.Escalated("escalated"),
                null, factory);
        driver.start();

        waitUntilState(driver, LoopState.PAUSED);
        StubInteractiveSession session = waitUntilReference(createdSession);

        // Verify ENTERED metadata
        assertFalse(enteredEvents.isEmpty());
        Event entered = enteredEvents.get(0);
        assertEquals("s-meta", entered.metadata().get("sessionId"));
        assertEquals("q-meta", entered.metadata().get("questionId"));
        assertEquals("meta-change", entered.metadata().get("changeName"));

        // Close session
        session.close();
        waitUntilNonEmpty(exitedEvents);

        // Verify EXITED metadata
        assertFalse(exitedEvents.isEmpty());
        Event exited = exitedEvents.get(0);
        assertEquals("s-meta", exited.metadata().get("sessionId"));
        assertEquals("q-meta", exited.metadata().get("questionId"));
        assertEquals("CLOSED", exited.metadata().get("sessionEndState"));

        driver.stop();
    }
}
