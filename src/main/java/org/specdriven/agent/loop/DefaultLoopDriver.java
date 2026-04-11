package org.specdriven.agent.loop;

import org.specdriven.agent.agent.ContextWindowManager;
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
    private final LoopAnswerAgent answerAgent;
    private final Object stateLock = new Object();

    private LoopState state = LoopState.IDLE;
    private final List<LoopIteration> completedIterations = new ArrayList<>();
    private final AtomicReference<LoopIteration> currentIteration = new AtomicReference<>();
    private final Set<String> completedChangeNames = new LinkedHashSet<>();
    private volatile Thread loopThread;
    private volatile boolean stopRequested = false;
    private volatile ContextWindowManager contextManager;

    public DefaultLoopDriver(LoopConfig config, LoopScheduler scheduler) {
        this(config, scheduler, new StubLoopPipeline(), null, null);
    }

    public DefaultLoopDriver(LoopConfig config, LoopScheduler scheduler, LoopPipeline pipeline) {
        this(config, scheduler, pipeline, null, null);
    }

    public DefaultLoopDriver(LoopConfig config, LoopScheduler scheduler, LoopPipeline pipeline,
                             LoopIterationStore store) {
        this(config, scheduler, pipeline, store, null);
    }

    public DefaultLoopDriver(LoopConfig config, LoopScheduler scheduler, LoopPipeline pipeline,
                             LoopIterationStore store, LoopAnswerAgent answerAgent) {
        this.config = config;
        this.scheduler = scheduler;
        this.pipeline = pipeline;
        this.store = store;
        this.answerAgent = answerAgent;
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
        Optional<LoopProgress> recovered = store != null ? store.loadProgress() : Optional.empty();
        recovered.ifPresent(progress -> {
            synchronized (stateLock) {
                completedChangeNames.addAll(progress.completedChangeNames());
                completedIterations.addAll(store.loadIterations());
            }
        });

        // Initialize context window manager if budget is configured
        if (config.contextBudget() != null) {
            ContextBudget budget = config.contextBudget();
            contextManager = new ContextWindowManager(budget.modelName(), budget.maxTokens());
            // Recover accumulated token usage from prior session
            recovered.ifPresent(progress -> {
                if (progress.tokenUsage() > 0) {
                    contextManager.addUsage(
                            new org.specdriven.agent.agent.LlmUsage(0, 0, (int) Math.min(progress.tokenUsage(), Integer.MAX_VALUE)));
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

                // Handle QUESTIONING: route to answer agent or escalate
                if (pipelineResult.status() == IterationStatus.QUESTIONING) {
                    publishEvent(EventType.LOOP_QUESTION_ROUTED, Map.of(
                            "questionId", pipelineResult.question().questionId(),
                            "changeName", c.changeName(),
                            "sessionId", pipelineResult.question().sessionId()
                    ));
                    synchronized (stateLock) {
                        state.requireTransitionTo(LoopState.QUESTIONING);
                        state = LoopState.QUESTIONING;
                    }

                    AnswerResolution resolution;
                    if (answerAgent == null) {
                        resolution = new AnswerResolution.Escalated("no answer agent configured");
                    } else {
                        resolution = answerAgent.resolve(
                                pipelineResult.question(), config.iterationTimeoutSeconds());
                    }

                    if (resolution instanceof AnswerResolution.Resolved resolved) {
                        publishEvent(EventType.LOOP_QUESTION_ANSWERED, Map.of(
                                "questionId", pipelineResult.question().questionId(),
                                "changeName", c.changeName(),
                                "confidence", resolved.answer().confidence()
                        ));
                        synchronized (stateLock) {
                            state.requireTransitionTo(LoopState.RUNNING);
                            state = LoopState.RUNNING;
                        }
                        pipelineResult = pipeline.execute(
                                c, config, Set.copyOf(pipelineResult.phasesCompleted()));
                        // Fall through to checkpoint/complete handling below
                    } else {
                        AnswerResolution.Escalated escalated = (AnswerResolution.Escalated) resolution;
                        publishEvent(EventType.LOOP_QUESTION_ESCALATED, Map.of(
                                "questionId", pipelineResult.question().questionId(),
                                "changeName", c.changeName(),
                                "reason", escalated.reason()
                        ));
                        synchronized (stateLock) {
                            state.requireTransitionTo(LoopState.PAUSED);
                            state = LoopState.PAUSED;
                        }
                        LoopIteration partial = new LoopIteration(
                                iterationCount, c.changeName(), c.milestoneFile(),
                                startedAt, System.currentTimeMillis(),
                                IterationStatus.QUESTIONING, escalated.reason()
                        );
                        synchronized (stateLock) {
                            completedIterations.add(partial);
                            currentIteration.set(null);
                        }
                        if (store != null) {
                            store.saveIteration(partial);
                            store.saveProgress(buildSnapshot(0));
                        }
                        synchronized (stateLock) {
                            try {
                                stateLock.wait();
                            } catch (InterruptedException e) {
                                if (stopRequested) return;
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                        continue;
                    }
                }

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
                    store.saveProgress(buildSnapshot(pipelineResult.tokenUsage()));
                }

                publishEvent(EventType.LOOP_ITERATION_COMPLETED, Map.of(
                        "iterationNumber", iterationCount,
                        "changeName", c.changeName()
                ));

                // Check context exhaustion
                if (contextManager != null) {
                    contextManager.addUsage(
                            new org.specdriven.agent.agent.LlmUsage(0, 0, (int) Math.min(pipelineResult.tokenUsage(), Integer.MAX_VALUE)));
                    ContextBudget budget = config.contextBudget();
                    int threshold = budget.maxTokens() * budget.warningThresholdPercent() / 100;
                    if (contextManager.remainingCapacity() < threshold) {
                        stopWithContextExhaustion(iterationCount, contextManager);
                        return;
                    }
                }
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
        return buildSnapshot(0);
    }

    private LoopProgress buildSnapshot(long tokenUsage) {
        synchronized (stateLock) {
            return new LoopProgress(state, Set.copyOf(completedChangeNames),
                    completedIterations.size(), tokenUsage);
        }
    }

    private void stopWithContextExhaustion(int completedIterations,
                                           ContextWindowManager ctxManager) {
        long totalUsage = ctxManager.maxTokens() - ctxManager.remainingCapacity();
        synchronized (stateLock) {
            if (state == LoopState.STOPPED) return;
            state = LoopState.STOPPED;
        }
        stopRequested = true;
        persistSnapshot(totalUsage);
        publishEvent(EventType.LOOP_CONTEXT_EXHAUSTED, Map.of(
                "tokenUsage", totalUsage,
                "maxTokens", ctxManager.maxTokens(),
                "remainingTokens", ctxManager.remainingCapacity(),
                "completedIterations", completedIterations
        ));
        publishEvent(EventType.LOOP_STOPPED, Map.of(
                "totalIterations", completedIterations,
                "reason", "context exhausted"
        ));
    }

    private void persistSnapshot() {
        persistSnapshot(0);
    }

    private void persistSnapshot(long tokenUsage) {
        if (store != null) {
            try {
                store.saveProgress(buildSnapshot(tokenUsage));
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
