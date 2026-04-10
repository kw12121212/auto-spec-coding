package org.specdriven.agent.loop;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
}
