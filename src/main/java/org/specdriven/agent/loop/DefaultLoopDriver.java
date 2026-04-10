package org.specdriven.agent.loop;

import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default implementation of LoopDriver using VirtualThread scheduling,
 * synchronized state transitions, and EventBus publication.
 */
public class DefaultLoopDriver implements LoopDriver {

    private static final Logger LOG = Logger.getLogger(DefaultLoopDriver.class.getName());

    private final LoopConfig config;
    private final LoopScheduler scheduler;
    private final LoopPipeline pipeline;
    private final LoopIterationStore store;
    private final Object stateLock = new Object();

    private LoopState state = LoopState.IDLE;
    private final List<LoopIteration> completedIterations = new ArrayList<>();
    private final AtomicReference<LoopIteration> currentIteration = new AtomicReference<>();
    private final Set<String> completedChangeNames = new LinkedHashSet<>();
    private volatile Thread loopThread;
    private volatile boolean stopRequested = false;

    public DefaultLoopDriver(LoopConfig config, LoopScheduler scheduler) {
        this(config, scheduler, new StubLoopPipeline(), null);
    }

    public DefaultLoopDriver(LoopConfig config, LoopScheduler scheduler, LoopPipeline pipeline) {
        this(config, scheduler, pipeline, null);
    }

    public DefaultLoopDriver(LoopConfig config, LoopScheduler scheduler, LoopPipeline pipeline, LoopIterationStore store) {
        this.config = config;
        this.scheduler = scheduler;
        this.pipeline = pipeline;
        this.store = store;
    }

    @Override
    public void start() {
        synchronized (stateLock) {
            state.requireTransitionTo(LoopState.RECOMMENDING);
            state = LoopState.RECOMMENDING;
        }
        publishEvent(EventType.LOOP_STARTED, Map.of());
        stopRequested = false;

        // Recover progress from store if available
        if (store != null) {
            store.loadProgress().ifPresent(progress -> {
                synchronized (stateLock) {
                    completedChangeNames.addAll(progress.completedChangeNames());
                    completedIterations.addAll(store.loadIterations());
                }
            });
        }

        loopThread = Thread.startVirtualThread(this::runLoop);
    }

    @Override
    public void pause() {
        synchronized (stateLock) {
            state.requireTransitionTo(LoopState.PAUSED);
            state = LoopState.PAUSED;
        }
        publishEvent(EventType.LOOP_PAUSED, Map.of());
    }

    @Override
    public void resume() {
        synchronized (stateLock) {
            state.requireTransitionTo(LoopState.RECOMMENDING);
            state = LoopState.RECOMMENDING;
            stateLock.notifyAll();
        }
        publishEvent(EventType.LOOP_RESUMED, Map.of());
    }

    @Override
    public void stop() {
        synchronized (stateLock) {
            if (state == LoopState.STOPPED) return;
            state = LoopState.STOPPED;
            stateLock.notifyAll();
        }
        stopRequested = true;
        if (loopThread != null) {
            loopThread.interrupt();
        }
        persistSnapshot();
        publishEvent(EventType.LOOP_STOPPED, Map.of(
                "totalIterations", completedIterations.size(),
                "reason", "user requested"
        ));
    }

    @Override
    public LoopState getState() {
        synchronized (stateLock) {
            return state;
        }
    }

    @Override
    public Optional<LoopIteration> getCurrentIteration() {
        return Optional.ofNullable(currentIteration.get());
    }

    @Override
    public List<LoopIteration> getCompletedIterations() {
        synchronized (stateLock) {
            return List.copyOf(completedIterations);
        }
    }

    @Override
    public LoopConfig getConfig() {
        return config;
    }

    private void runLoop() {
        int iterationCount = completedIterations.size();
        try {
            while (!stopRequested && !Thread.interrupted()) {
                // Check max iterations
                if (iterationCount >= config.maxIterations()) {
                    stopWithReason("max iterations reached");
                    return;
                }

                // Check if paused
                synchronized (stateLock) {
                    if (state == LoopState.PAUSED) {
                        try {
                            stateLock.wait();
                        } catch (InterruptedException e) {
                            if (stopRequested) return;
                            Thread.currentThread().interrupt();
                            return;
                        }
                        continue;
                    }
                    if (state == LoopState.STOPPED) return;
                }

                // Recommend phase
                synchronized (stateLock) {
                    if (state == LoopState.RECOMMENDING) {
                        state = LoopState.RUNNING;
                    }
                }

                iterationCount++;
                long startedAt = System.currentTimeMillis();

                // Build context and select next
                LoopContext ctx = new LoopContext(
                        "", "", List.of(),
                        Set.copyOf(completedChangeNames)
                );

                Optional<LoopCandidate> candidate = scheduler.selectNext(ctx);

                if (candidate.isEmpty()) {
                    stopWithReason("no more candidates");
                    return;
                }

                LoopCandidate c = candidate.get();
                LoopIteration iteration = new LoopIteration(
                        iterationCount, c.changeName(), c.milestoneFile(),
                        startedAt, null, IterationStatus.SUCCESS, null
                );
                currentIteration.set(iteration);

                // Execute pipeline
                IterationResult pipelineResult = pipeline.execute(c, config);

                // Checkpoint
                synchronized (stateLock) {
                    if (state == LoopState.RUNNING) {
                        state = LoopState.CHECKPOINT;
                    }
                }

                // Mark iteration complete
                LoopIteration completed = new LoopIteration(
                        iterationCount, c.changeName(), c.milestoneFile(),
                        startedAt, System.currentTimeMillis(),
                        pipelineResult.status(), pipelineResult.failureReason()
                );

                synchronized (stateLock) {
                    completedIterations.add(completed);
                    completedChangeNames.add(c.changeName());
                    currentIteration.set(null);
                    if (state == LoopState.CHECKPOINT) {
                        state = LoopState.RECOMMENDING;
                    }
                }

                // Persist iteration and progress snapshot
                if (store != null) {
                    store.saveIteration(completed);
                    store.saveProgress(buildSnapshot());
                }

                publishEvent(EventType.LOOP_ITERATION_COMPLETED, Map.of(
                        "iterationNumber", iterationCount,
                        "changeName", c.changeName()
                ));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Loop error", e);
            synchronized (stateLock) {
                if (state != LoopState.STOPPED) {
                    state = LoopState.ERROR;
                }
            }
            publishEvent(EventType.LOOP_ERROR, Map.of(
                    "error", e.getMessage() != null ? e.getMessage() : "unknown"
            ));
        }
    }

    private void stopWithReason(String reason) {
        synchronized (stateLock) {
            if (state == LoopState.STOPPED) return;
            state = LoopState.STOPPED;
        }
        stopRequested = true;
        persistSnapshot();
        publishEvent(EventType.LOOP_STOPPED, Map.of(
                "totalIterations", completedIterations.size(),
                "reason", reason
        ));
    }

    private LoopProgress buildSnapshot() {
        synchronized (stateLock) {
            return new LoopProgress(state, Set.copyOf(completedChangeNames), completedIterations.size());
        }
    }

    private void persistSnapshot() {
        if (store != null) {
            try {
                store.saveProgress(buildSnapshot());
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to persist progress on stop", e);
            }
        }
    }

    private void publishEvent(EventType type, Map<String, Object> metadata) {
        try {
            EventBus bus = config.eventBus();
            bus.publish(new Event(type, System.currentTimeMillis(), "LoopDriver", metadata));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to publish event: " + type, e);
        }
    }
}
