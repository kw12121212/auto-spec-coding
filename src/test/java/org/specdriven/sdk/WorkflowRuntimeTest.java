package org.specdriven.sdk;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.EventType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowRuntimeTest {

    @Test
    void domainDeclarationMakesWorkflowStartable() {
        SpecDriven sdk = SpecDriven.builder().build();
        try {
            sdk.declareWorkflow("invoice-approval");

            WorkflowInstanceView started = sdk.startWorkflow("invoice-approval", Map.of("invoiceId", "inv-1"));

            assertNotNull(started.workflowId());
            assertEquals("invoice-approval", started.workflowName());
            assertEquals(WorkflowStatus.ACCEPTED, started.status());
        } finally {
            sdk.close();
        }
    }

    @Test
    void sqlDeclarationMakesWorkflowStartable() {
        SpecDriven sdk = SpecDriven.builder().build();
        try {
            sdk.declareWorkflowSql("CREATE WORKFLOW invoice-approval;");

            WorkflowInstanceView started = sdk.startWorkflow("invoice-approval", Map.of());

            assertNotNull(started.workflowId());
            assertEquals("invoice-approval", started.workflowName());
        } finally {
            sdk.close();
        }
    }

    @Test
    void unsupportedWorkflowDeclarationIsRejected() {
        SpecDriven sdk = SpecDriven.builder().build();
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> sdk.declareWorkflowSql("DROP WORKFLOW invoice-approval;"));
        } finally {
            sdk.close();
        }
    }

    @Test
    void stateAndResultCanBeQueriedAfterStart() {
        SpecDriven sdk = SpecDriven.builder().build();
        try {
            sdk.declareWorkflow("invoice-approval");
            WorkflowInstanceView started = sdk.startWorkflow("invoice-approval", Map.of("invoiceId", "inv-1"));

            WorkflowResultView result = awaitResult(sdk, started.workflowId(), Duration.ofSeconds(5));

            assertEquals(started.workflowId(), sdk.workflowState(started.workflowId()).workflowId());
            assertEquals(WorkflowStatus.SUCCEEDED, result.status());
            assertTrue(result.result() instanceof Map<?, ?>);
            assertNull(result.failureSummary());
        } finally {
            sdk.close();
        }
    }

    @Test
    void waitingWorkflowRemainsObservable() {
        SpecDriven sdk = SpecDriven.builder().build();
        try {
            sdk.declareWorkflow("human-approval");
            WorkflowInstanceView started = sdk.startWorkflow("human-approval", Map.of("waitForInput", true));

            WorkflowInstanceView waiting = awaitStatus(sdk, started.workflowId(), WorkflowStatus.WAITING_FOR_INPUT,
                    Duration.ofSeconds(5));
            WorkflowResultView result = sdk.workflowResult(started.workflowId());

            assertEquals(WorkflowStatus.WAITING_FOR_INPUT, waiting.status());
            assertEquals(WorkflowStatus.WAITING_FOR_INPUT, result.status());
            assertNull(result.result());
        } finally {
            sdk.close();
        }
    }

    @Test
    void workflowLifecyclePublishesEvents() {
        SpecDriven sdk = SpecDriven.builder().build();
        try {
            List<EventType> received = new ArrayList<>();
            sdk.eventBus().subscribe(EventType.WORKFLOW_DECLARED, event -> received.add(event.type()));
            sdk.eventBus().subscribe(EventType.WORKFLOW_STARTED, event -> received.add(event.type()));
            sdk.eventBus().subscribe(EventType.WORKFLOW_STATE_CHANGED, event -> received.add(event.type()));
            sdk.eventBus().subscribe(EventType.WORKFLOW_COMPLETED, event -> received.add(event.type()));

            sdk.declareWorkflow("invoice-approval");
            WorkflowInstanceView started = sdk.startWorkflow("invoice-approval", Map.of());
            awaitResult(sdk, started.workflowId(), Duration.ofSeconds(5));

            awaitEventTypes(received,
                    List.of(EventType.WORKFLOW_DECLARED, EventType.WORKFLOW_STARTED,
                            EventType.WORKFLOW_STATE_CHANGED, EventType.WORKFLOW_COMPLETED),
                    Duration.ofSeconds(5));
        } finally {
            sdk.close();
        }
    }

    @Test
    void workflowEventsCarryRequiredAuditMetadata() {
        SpecDriven sdk = SpecDriven.builder().build();
        try {
            List<org.specdriven.agent.event.Event> received = new ArrayList<>();
            sdk.eventBus().subscribe(EventType.WORKFLOW_DECLARED, received::add);
            sdk.eventBus().subscribe(EventType.WORKFLOW_STARTED, received::add);
            sdk.eventBus().subscribe(EventType.WORKFLOW_STATE_CHANGED, received::add);
            sdk.eventBus().subscribe(EventType.WORKFLOW_FAILED, received::add);

            sdk.declareWorkflow("invoice-approval");
            WorkflowInstanceView started = sdk.startWorkflow("invoice-approval", Map.of("fail", true));
            WorkflowResultView result = awaitResult(sdk, started.workflowId(), Duration.ofSeconds(5));

            assertEquals(WorkflowStatus.FAILED, result.status());

            org.specdriven.agent.event.Event declared = received.stream()
                    .filter(event -> event.type() == EventType.WORKFLOW_DECLARED)
                    .findFirst()
                    .orElseThrow();
            assertEquals("invoice-approval", declared.metadata().get("workflowName"));
            assertEquals("domain", declared.metadata().get("declarationPath"));

            org.specdriven.agent.event.Event startedEvent = received.stream()
                    .filter(event -> event.type() == EventType.WORKFLOW_STARTED)
                    .findFirst()
                    .orElseThrow();
            assertEquals(started.workflowId(), startedEvent.metadata().get("workflowId"));
            assertEquals("invoice-approval", startedEvent.metadata().get("workflowName"));

            org.specdriven.agent.event.Event stateChanged = received.stream()
                    .filter(event -> event.type() == EventType.WORKFLOW_STATE_CHANGED)
                    .findFirst()
                    .orElseThrow();
            assertEquals(started.workflowId(), stateChanged.metadata().get("workflowId"));
            assertEquals("invoice-approval", stateChanged.metadata().get("workflowName"));
            assertNotNull(stateChanged.metadata().get("fromStatus"));
            assertNotNull(stateChanged.metadata().get("toStatus"));

            org.specdriven.agent.event.Event failed = received.stream()
                    .filter(event -> event.type() == EventType.WORKFLOW_FAILED)
                    .findFirst()
                    .orElseThrow();
            assertEquals(started.workflowId(), failed.metadata().get("workflowId"));
            assertEquals("invoice-approval", failed.metadata().get("workflowName"));
            assertEquals("Workflow failed due to requested failure", failed.metadata().get("failureReason"));
        } finally {
            sdk.close();
        }
    }

    @Test
    void unknownWorkflowNameAndInstanceAreRejected() {
        SpecDriven sdk = SpecDriven.builder().build();
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> sdk.startWorkflow("missing-workflow", Map.of()));
            assertThrows(IllegalArgumentException.class,
                    () -> sdk.workflowState("missing-id"));
            assertThrows(IllegalArgumentException.class,
                    () -> sdk.workflowResult("missing-id"));
        } finally {
            sdk.close();
        }
    }

    @Test
    void persistedRecoveryKeepsWorkflowIdAndResumesFromCorrectBoundary() {
        org.specdriven.agent.event.SimpleEventBus delegateBus = new org.specdriven.agent.event.SimpleEventBus();
        CrashingEventBus eventBus = new CrashingEventBus(delegateBus, event ->
                event.type() == EventType.WORKFLOW_STEP_STARTED
                        && Integer.valueOf(1).equals(event.metadata().get("stepIndex")));
        WorkflowRuntime.InMemoryStateStore stateStore = new WorkflowRuntime.InMemoryStateStore();
        List<String> execution = new CopyOnWriteArrayList<>();
        List<org.specdriven.agent.event.Event> recoveredEvents = new CopyOnWriteArrayList<>();

        WorkflowStepExecutor stepA = new WorkflowStepExecutor() {
            @Override
            public WorkflowStep.StepType stepType() {
                return WorkflowStep.StepType.TOOL;
            }

            @Override
            public WorkflowStepResult execute(WorkflowStep step, Map<String, Object> input) {
                execution.add("step-a");
                return WorkflowStepResult.success(Map.of("a", "done"));
            }
        };

        WorkflowStepExecutor stepB = new WorkflowStepExecutor() {
            @Override
            public WorkflowStep.StepType stepType() {
                return WorkflowStep.StepType.SERVICE;
            }

            @Override
            public WorkflowStepResult execute(WorkflowStep step, Map<String, Object> input) {
                execution.add("step-b");
                return WorkflowStepResult.success(Map.of("b", input.get("a")));
            }
        };

        WorkflowRuntime firstRuntime = new WorkflowRuntime(eventBus, List.of(stepA, stepB), () -> null, stateStore);
        String workflowId;
        try {
            firstRuntime.declareWorkflow("recoverable", List.of(
                    new WorkflowStep(WorkflowStep.StepType.TOOL, "step-a"),
                    new WorkflowStep(WorkflowStep.StepType.SERVICE, "step-b")));

            WorkflowInstanceView started = firstRuntime.startWorkflow("recoverable", Map.of());
            workflowId = started.workflowId();
            awaitExecution(execution, List.of("step-a"), Duration.ofSeconds(5));
        } finally {
            firstRuntime.close();
        }

        execution.clear();

        org.specdriven.agent.event.SimpleEventBus recoveryBus = new org.specdriven.agent.event.SimpleEventBus();
        recoveryBus.subscribe(EventType.WORKFLOW_RECOVERED, recoveredEvents::add);
        WorkflowRuntime recoveredRuntime = new WorkflowRuntime(recoveryBus, List.of(stepA, stepB), () -> null, stateStore);
        try {
            WorkflowResultView result = awaitResult(recoveredRuntime, workflowId, Duration.ofSeconds(5));

            assertEquals(WorkflowStatus.SUCCEEDED, result.status());
            assertEquals(List.of("step-b"), execution);
            assertEquals(workflowId, recoveredRuntime.workflowState(workflowId).workflowId());
            assertFalse(recoveredEvents.isEmpty());
            assertEquals(workflowId, recoveredEvents.get(0).metadata().get("workflowId"));
            assertEquals("recoverable", recoveredEvents.get(0).metadata().get("workflowName"));
            assertEquals(1, recoveredEvents.get(0).metadata().get("resumeFromStepIndex"));
        } finally {
            recoveredRuntime.close();
        }
    }

    @Test
    void recoveredWaitingWorkflowKeepsQuestionCorrelationAndResumesThroughExistingAnswerPath() {
        org.specdriven.agent.event.SimpleEventBus eventBus = new org.specdriven.agent.event.SimpleEventBus();
        WorkflowRuntime.InMemoryStateStore stateStore = new WorkflowRuntime.InMemoryStateStore();
        List<String> deliveredSessionIds = new CopyOnWriteArrayList<>();
        List<org.specdriven.agent.event.Event> checkpointEvents = new CopyOnWriteArrayList<>();
        List<org.specdriven.agent.event.Event> recoveredEvents = new CopyOnWriteArrayList<>();
        eventBus.subscribe(EventType.WORKFLOW_CHECKPOINT_SAVED, checkpointEvents::add);
        eventBus.subscribe(EventType.WORKFLOW_RECOVERED, recoveredEvents::add);

        org.specdriven.agent.question.QuestionDeliveryService deliveryService = new org.specdriven.agent.question.QuestionDeliveryService(
                new org.specdriven.agent.question.QuestionDeliveryChannel() {
                    @Override
                    public void send(org.specdriven.agent.question.Question q) {
                        deliveredSessionIds.add(q.sessionId());
                    }

                    @Override
                    public void close() {}
                },
                new org.specdriven.agent.question.QuestionReplyCollector() {
                    @Override
                    public void collect(String sessionId, String questionId, org.specdriven.agent.question.Answer answer) {}

                    @Override
                    public void close() {}
                },
                new org.specdriven.agent.question.QuestionRuntime(eventBus),
                new org.specdriven.agent.question.QuestionStore() {
                    private final java.util.concurrent.ConcurrentHashMap<String, org.specdriven.agent.question.Question> store = new java.util.concurrent.ConcurrentHashMap<>();

                    @Override
                    public String save(org.specdriven.agent.question.Question question) {
                        store.put(question.questionId(), question);
                        return question.questionId();
                    }

                    @Override
                    public org.specdriven.agent.question.Question update(String questionId, org.specdriven.agent.question.QuestionStatus status) {
                        return store.get(questionId);
                    }

                    @Override
                    public List<org.specdriven.agent.question.Question> findBySession(String sessionId) {
                        return store.values().stream().filter(q -> sessionId.equals(q.sessionId())).toList();
                    }

                    @Override
                    public List<org.specdriven.agent.question.Question> findByStatus(org.specdriven.agent.question.QuestionStatus status) {
                        return store.values().stream().filter(q -> q.status() == status).toList();
                    }

                    @Override
                    public java.util.Optional<org.specdriven.agent.question.Question> findPending(String sessionId) {
                        return store.values().stream().filter(q -> sessionId.equals(q.sessionId())).findFirst();
                    }

                    @Override
                    public void delete(String questionId) {
                        store.remove(questionId);
                    }
                });

        WorkflowRuntime firstRuntime = new WorkflowRuntime(
                eventBus,
                List.of(
                        new WorkflowStepExecutor() {
                            @Override
                            public WorkflowStep.StepType stepType() {
                                return WorkflowStep.StepType.TOOL;
                            }

                            @Override
                            public WorkflowStepResult execute(WorkflowStep step, Map<String, Object> input) {
                                return WorkflowStepResult.awaitingInput("approve?");
                            }
                        },
                        new WorkflowStepExecutor() {
                            @Override
                            public WorkflowStep.StepType stepType() {
                                return WorkflowStep.StepType.SERVICE;
                            }

                            @Override
                            public WorkflowStepResult execute(WorkflowStep step, Map<String, Object> input) {
                                return WorkflowStepResult.success(Map.of("humanInput", input.get("humanInput")));
                            }
                        }),
                deliveryService,
                stateStore);
        String workflowId;
        try {
            firstRuntime.declareWorkflow("waiting-recovery", List.of(
                    new WorkflowStep(WorkflowStep.StepType.TOOL, "pause-step"),
                    new WorkflowStep(WorkflowStep.StepType.SERVICE, "finish-step")));

            WorkflowInstanceView started = firstRuntime.startWorkflow("waiting-recovery", Map.of());
            workflowId = started.workflowId();
            awaitStatus(firstRuntime, workflowId, WorkflowStatus.WAITING_FOR_INPUT, Duration.ofSeconds(5));
            assertEquals(List.of(workflowId), deliveredSessionIds);

            org.specdriven.agent.event.Event waitingCheckpoint = awaitCheckpointEvent(
                    checkpointEvents,
                    workflowId,
                    WorkflowStatus.WAITING_FOR_INPUT,
                    0,
                    Duration.ofSeconds(5));
            assertEquals("waiting-recovery", waitingCheckpoint.metadata().get("workflowName"));
            assertEquals(1, waitingCheckpoint.metadata().get("resumeFromStepIndex"));
        } finally {
            firstRuntime.close();
        }

        WorkflowRuntime recoveredRuntime = new WorkflowRuntime(eventBus, List.of(
                new WorkflowStepExecutor() {
                    @Override
                    public WorkflowStep.StepType stepType() {
                        return WorkflowStep.StepType.TOOL;
                    }

                    @Override
                    public WorkflowStepResult execute(WorkflowStep step, Map<String, Object> input) {
                        return WorkflowStepResult.awaitingInput("approve?");
                    }
                },
                new WorkflowStepExecutor() {
                    @Override
                    public WorkflowStep.StepType stepType() {
                        return WorkflowStep.StepType.SERVICE;
                    }

                    @Override
                    public WorkflowStepResult execute(WorkflowStep step, Map<String, Object> input) {
                        return WorkflowStepResult.success(Map.of("humanInput", input.get("humanInput")));
                    }
                }), deliveryService, stateStore);
        try {
            WorkflowInstanceView waiting = recoveredRuntime.workflowState(workflowId);
            assertEquals(WorkflowStatus.WAITING_FOR_INPUT, waiting.status());

            org.specdriven.agent.event.Event recovered = awaitRecoveredEvent(
                    recoveredEvents,
                    workflowId,
                    Duration.ofSeconds(5));
            assertEquals("waiting-recovery", recovered.metadata().get("workflowName"));
            assertEquals(WorkflowStatus.WAITING_FOR_INPUT.name(), recovered.metadata().get("status"));
            assertEquals(1, recovered.metadata().get("resumeFromStepIndex"));

            eventBus.publish(new org.specdriven.agent.event.Event(
                    EventType.QUESTION_ANSWERED,
                    System.currentTimeMillis(),
                    workflowId,
                    Map.of("sessionId", workflowId, "questionId", "q-1", "content", "approved")));

            WorkflowResultView result = awaitResult(recoveredRuntime, workflowId, Duration.ofSeconds(5));
            assertEquals(WorkflowStatus.SUCCEEDED, result.status());
            assertTrue(result.result() instanceof Map<?, ?>);
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result.result();
            assertEquals("approved", resultMap.get("humanInput"));
        } finally {
            recoveredRuntime.close();
        }
    }

    @Test
    void resumedWorkflowPublishesCheckpointForRunningStateBeforeFinishing() {
        org.specdriven.agent.event.SimpleEventBus eventBus = new org.specdriven.agent.event.SimpleEventBus();
        WorkflowRuntime.InMemoryStateStore stateStore = new WorkflowRuntime.InMemoryStateStore();
        List<org.specdriven.agent.event.Event> checkpointEvents = new CopyOnWriteArrayList<>();
        eventBus.subscribe(EventType.WORKFLOW_CHECKPOINT_SAVED, checkpointEvents::add);

        org.specdriven.agent.question.QuestionDeliveryService deliveryService = new org.specdriven.agent.question.QuestionDeliveryService(
                new org.specdriven.agent.question.QuestionDeliveryChannel() {
                    @Override
                    public void send(org.specdriven.agent.question.Question q) {}

                    @Override
                    public void close() {}
                },
                new org.specdriven.agent.question.QuestionReplyCollector() {
                    @Override
                    public void collect(String sessionId, String questionId, org.specdriven.agent.question.Answer answer) {}

                    @Override
                    public void close() {}
                },
                new org.specdriven.agent.question.QuestionRuntime(eventBus),
                new org.specdriven.agent.question.QuestionStore() {
                    private final java.util.concurrent.ConcurrentHashMap<String, org.specdriven.agent.question.Question> store = new java.util.concurrent.ConcurrentHashMap<>();

                    @Override
                    public String save(org.specdriven.agent.question.Question question) {
                        store.put(question.questionId(), question);
                        return question.questionId();
                    }

                    @Override
                    public org.specdriven.agent.question.Question update(String questionId, org.specdriven.agent.question.QuestionStatus status) {
                        return store.get(questionId);
                    }

                    @Override
                    public List<org.specdriven.agent.question.Question> findBySession(String sessionId) {
                        return store.values().stream().filter(q -> sessionId.equals(q.sessionId())).toList();
                    }

                    @Override
                    public List<org.specdriven.agent.question.Question> findByStatus(org.specdriven.agent.question.QuestionStatus status) {
                        return store.values().stream().filter(q -> q.status() == status).toList();
                    }

                    @Override
                    public java.util.Optional<org.specdriven.agent.question.Question> findPending(String sessionId) {
                        return store.values().stream().filter(q -> sessionId.equals(q.sessionId())).findFirst();
                    }

                    @Override
                    public void delete(String questionId) {
                        store.remove(questionId);
                    }
                });

        WorkflowRuntime runtime = new WorkflowRuntime(
                eventBus,
                List.of(
                        new WorkflowStepExecutor() {
                            @Override
                            public WorkflowStep.StepType stepType() {
                                return WorkflowStep.StepType.TOOL;
                            }

                            @Override
                            public WorkflowStepResult execute(WorkflowStep step, Map<String, Object> input) {
                                return WorkflowStepResult.awaitingInput("approve?");
                            }
                        },
                        new WorkflowStepExecutor() {
                            @Override
                            public WorkflowStep.StepType stepType() {
                                return WorkflowStep.StepType.SERVICE;
                            }

                            @Override
                            public WorkflowStepResult execute(WorkflowStep step, Map<String, Object> input) {
                                return WorkflowStepResult.success(Map.of("humanInput", input.get("humanInput")));
                            }
                        }),
                deliveryService,
                stateStore);
        try {
            runtime.declareWorkflow("resume-checkpoint", List.of(
                    new WorkflowStep(WorkflowStep.StepType.TOOL, "pause-step"),
                    new WorkflowStep(WorkflowStep.StepType.SERVICE, "finish-step")));

            WorkflowInstanceView started = runtime.startWorkflow("resume-checkpoint", Map.of());
            awaitStatus(runtime, started.workflowId(), WorkflowStatus.WAITING_FOR_INPUT, Duration.ofSeconds(5));
            int checkpointCountBeforeResume = checkpointEvents.size();

            eventBus.publish(new org.specdriven.agent.event.Event(
                    EventType.QUESTION_ANSWERED,
                    System.currentTimeMillis(),
                    started.workflowId(),
                    Map.of("sessionId", started.workflowId(), "questionId", "q-1", "content", "approved")));

            awaitResult(runtime, started.workflowId(), Duration.ofSeconds(5));

            org.specdriven.agent.event.Event runningCheckpoint = awaitCheckpointEvent(
                    checkpointEvents,
                    started.workflowId(),
                    WorkflowStatus.RUNNING,
                    checkpointCountBeforeResume,
                    Duration.ofSeconds(5));
            assertEquals("resume-checkpoint", runningCheckpoint.metadata().get("workflowName"));
            assertEquals(1, runningCheckpoint.metadata().get("resumeFromStepIndex"));
        } finally {
            runtime.close();
        }
    }

    @Test
    void retryExhaustionProducesDiagnosableFailureAndAuditMetadata() {
        SpecDriven sdk = SpecDriven.builder().build();
        try {
            List<org.specdriven.agent.event.Event> failedEvents = new CopyOnWriteArrayList<>();
            sdk.eventBus().subscribe(EventType.WORKFLOW_FAILED, failedEvents::add);

            WorkflowRuntime runtime = new WorkflowRuntime(sdk.eventBus(), List.of(
                    new WorkflowStepExecutor() {
                        @Override
                        public WorkflowStep.StepType stepType() {
                            return WorkflowStep.StepType.TOOL;
                        }

                        @Override
                        public WorkflowStepResult execute(WorkflowStep step, Map<String, Object> input) {
                            return WorkflowStepResult.retryableFailure("timeout");
                        }
                    }), sdk.deliveryService(), new WorkflowRuntime.InMemoryStateStore());
            try {
                runtime.declareWorkflow("retry-diagnostics",
                        List.of(new WorkflowStep(WorkflowStep.StepType.TOOL, "retry-step")));

                WorkflowInstanceView started = runtime.startWorkflow("retry-diagnostics", Map.of());
                WorkflowResultView result = awaitResult(runtime, started.workflowId(), Duration.ofSeconds(5));

                assertEquals(WorkflowStatus.FAILED, result.status());
                assertTrue(result.failureSummary().contains("retry-step"));
                assertTrue(result.failureSummary().contains("timeout"));
                assertTrue(result.failureSummary().contains("retry exhaustion"));

                org.specdriven.agent.event.Event failed = awaitFailedWorkflowEvent(
                        failedEvents,
                        started.workflowId(),
                        Duration.ofSeconds(5));
                assertEquals(Boolean.TRUE, failed.metadata().get("retryExhausted"));
                assertEquals("retry-step", failed.metadata().get("failedStepName"));
                assertEquals("timeout", failed.metadata().get("failureReason"));
            } finally {
                runtime.close();
            }
        } finally {
            sdk.close();
        }
    }

    private WorkflowResultView awaitResult(SpecDriven sdk, String workflowId, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        WorkflowResultView last = null;
        while (System.nanoTime() < deadline) {
            last = sdk.workflowResult(workflowId);
            if (last.status() == WorkflowStatus.SUCCEEDED || last.status() == WorkflowStatus.FAILED
                    || last.status() == WorkflowStatus.CANCELLED) {
                return last;
            }
            sleepBriefly();
        }
        fail("Timed out waiting for workflow result, last status=" + (last == null ? "null" : last.status()));
        return null;
    }

    private WorkflowResultView awaitResult(WorkflowRuntime runtime, String workflowId, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        WorkflowResultView last = null;
        while (System.nanoTime() < deadline) {
            last = runtime.workflowResult(workflowId);
            if (last.status() == WorkflowStatus.SUCCEEDED || last.status() == WorkflowStatus.FAILED
                    || last.status() == WorkflowStatus.CANCELLED) {
                return last;
            }
            sleepBriefly();
        }
        fail("Timed out waiting for workflow result, last status=" + (last == null ? "null" : last.status()));
        return null;
    }

    private WorkflowInstanceView awaitStatus(SpecDriven sdk, String workflowId, WorkflowStatus expected, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        WorkflowInstanceView last = null;
        while (System.nanoTime() < deadline) {
            last = sdk.workflowState(workflowId);
            if (last.status() == expected) {
                return last;
            }
            sleepBriefly();
        }
        fail("Timed out waiting for workflow status " + expected + ", last status="
                + (last == null ? "null" : last.status()));
        return null;
    }

    private WorkflowInstanceView awaitStatus(WorkflowRuntime runtime, String workflowId,
                                             WorkflowStatus expected, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        WorkflowInstanceView last = null;
        while (System.nanoTime() < deadline) {
            last = runtime.workflowState(workflowId);
            if (last.status() == expected) {
                return last;
            }
            sleepBriefly();
        }
        fail("Timed out waiting for workflow status " + expected + ", last status="
                + (last == null ? "null" : last.status()));
        return null;
    }

    private void awaitExecution(List<String> execution, List<String> expected, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (execution.equals(expected)) {
                return;
            }
            sleepBriefly();
        }
        fail("Timed out waiting for execution order " + expected + ", last execution=" + execution);
    }

    private void awaitEventTypes(List<EventType> received, List<EventType> expected, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (received.containsAll(expected)) {
                return;
            }
            sleepBriefly();
        }
        fail("Timed out waiting for event types " + expected + ", last events=" + received);
    }

    private org.specdriven.agent.event.Event awaitFailedWorkflowEvent(
            List<org.specdriven.agent.event.Event> failedEvents,
            String workflowId,
            Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            for (org.specdriven.agent.event.Event event : failedEvents) {
                if (workflowId.equals(event.metadata().get("workflowId"))
                        && Boolean.TRUE.equals(event.metadata().get("retryExhausted"))) {
                    return event;
                }
            }
            sleepBriefly();
        }
        fail("Timed out waiting for retry-exhausted workflow failure event for workflowId=" + workflowId);
        return null;
    }

    private org.specdriven.agent.event.Event awaitCheckpointEvent(
            List<org.specdriven.agent.event.Event> checkpointEvents,
            String workflowId,
            WorkflowStatus expectedStatus,
            int startIndex,
            Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            for (int index = Math.max(0, startIndex); index < checkpointEvents.size(); index++) {
                org.specdriven.agent.event.Event event = checkpointEvents.get(index);
                if (workflowId.equals(event.metadata().get("workflowId"))
                        && expectedStatus.name().equals(event.metadata().get("status"))) {
                    return event;
                }
            }
            sleepBriefly();
        }
        fail("Timed out waiting for checkpoint event for workflowId=" + workflowId + " and status=" + expectedStatus);
        return null;
    }

    private org.specdriven.agent.event.Event awaitRecoveredEvent(
            List<org.specdriven.agent.event.Event> recoveredEvents,
            String workflowId,
            Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            for (org.specdriven.agent.event.Event event : recoveredEvents) {
                if (workflowId.equals(event.metadata().get("workflowId"))) {
                    return event;
                }
            }
            sleepBriefly();
        }
        fail("Timed out waiting for recovered event for workflowId=" + workflowId);
        return null;
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(10L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted while waiting for workflow state");
        }
    }

    private static final class CrashingEventBus implements org.specdriven.agent.event.EventBus {
        private final org.specdriven.agent.event.EventBus delegate;
        private final java.util.function.Predicate<org.specdriven.agent.event.Event> crashCondition;
        private final java.util.concurrent.atomic.AtomicBoolean crashed = new java.util.concurrent.atomic.AtomicBoolean();

        private CrashingEventBus(org.specdriven.agent.event.EventBus delegate,
                                 java.util.function.Predicate<org.specdriven.agent.event.Event> crashCondition) {
            this.delegate = delegate;
            this.crashCondition = crashCondition;
        }

        @Override
        public void subscribe(EventType type, java.util.function.Consumer<org.specdriven.agent.event.Event> listener) {
            delegate.subscribe(type, listener);
        }

        @Override
        public void publish(org.specdriven.agent.event.Event event) {
            delegate.publish(event);
            if (crashCondition.test(event) && crashed.compareAndSet(false, true)) {
                throw new RuntimeException("Simulated runtime crash after checkpoint persistence");
            }
        }

        @Override
        public void unsubscribe(EventType type, java.util.function.Consumer<org.specdriven.agent.event.Event> listener) {
            delegate.unsubscribe(type, listener);
        }
    }
}
