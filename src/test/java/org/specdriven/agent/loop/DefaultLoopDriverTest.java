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
        public IterationResult execute(LoopCandidate candidate, LoopConfig config, Set<PipelinePhase> skipPhases) {
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
                        List.of(PipelinePhase.PROPOSE), 0, question);
            }
            return new IterationResult(IterationStatus.SUCCESS, null, 10,
                    List.of(PipelinePhase.IMPLEMENT, PipelinePhase.VERIFY,
                            PipelinePhase.REVIEW, PipelinePhase.ARCHIVE));
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

        Thread.sleep(1000);

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

        Thread.sleep(500);

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

        Thread.sleep(500);

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

        Thread.sleep(1000);

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

        Thread.sleep(1000);

        assertFalse(answeredEvents.isEmpty());
        Event answered = answeredEvents.get(0);
        assertEquals("q-test", answered.metadata().get("questionId"));
        assertEquals(0.8, (double) answered.metadata().get("confidence"), 0.001);

        driver.stop();
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

        Thread.sleep(500);

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

        Thread.sleep(500);

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

        Thread.sleep(500);

        assertEquals(LoopState.PAUSED, driver.getState());
        LoopProgress progress = store.loadProgress().orElseThrow();
        assertEquals(LoopState.PAUSED, progress.loopState());
        assertFalse(progress.completedChangeNames().contains("approval-change"));
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
        Thread.sleep(300);

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
        Thread.sleep(500);

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

        Thread.sleep(500);

        assertEquals(LoopState.PAUSED, driver.getState());
        assertEquals(1, channel.sent.size());
        assertEquals("q-delivery", channel.sent.get(0).questionId());
        assertTrue(deliveryService.pendingQuestion("session-delivery").isPresent());

        driver.stop();
    }

    private static void waitUntilState(DefaultLoopDriver driver, LoopState expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (driver.getState() == expected) {
                return;
            }
            Thread.sleep(25);
        }
        fail("Expected state " + expected + " but got " + driver.getState());
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
}
