package org.specdriven.sdk;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowStepCompositionTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private EventBus newEventBus() {
        return new org.specdriven.agent.event.SimpleEventBus();
    }

    private WorkflowRuntime runtimeWith(EventBus eventBus, WorkflowStepExecutor... executors) {
        return new WorkflowRuntime(eventBus, List.of(executors));
    }

    private WorkflowStepExecutor stubExecutor(WorkflowStep.StepType type,
                                               Map<String, Object> output,
                                               List<Map<String, Object>> capturedInputs) {
        return new WorkflowStepExecutor() {
            @Override
            public WorkflowStep.StepType stepType() {
                return type;
            }

            @Override
            public WorkflowStepResult execute(WorkflowStep step, Map<String, Object> input) {
                if (capturedInputs != null) {
                    capturedInputs.add(input);
                }
                return WorkflowStepResult.success(output);
            }
        };
    }

    private WorkflowStepExecutor failingExecutor(WorkflowStep.StepType type, String reason) {
        return new WorkflowStepExecutor() {
            @Override
            public WorkflowStep.StepType stepType() {
                return type;
            }

            @Override
            public WorkflowStepResult execute(WorkflowStep step, Map<String, Object> input) {
                return WorkflowStepResult.failure(reason);
            }
        };
    }

    private WorkflowResultView awaitResult(WorkflowRuntime runtime, String workflowId, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        WorkflowResultView last = null;
        while (System.nanoTime() < deadline) {
            last = runtime.workflowResult(workflowId);
            if (last.status() == WorkflowStatus.SUCCEEDED
                    || last.status() == WorkflowStatus.FAILED
                    || last.status() == WorkflowStatus.CANCELLED) {
                return last;
            }
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted waiting for result");
            }
        }
        fail("Timed out waiting for workflow result; last status=" + (last == null ? "null" : last.status()));
        return null;
    }

    // -----------------------------------------------------------------------
    // Scenario: single-step tool workflow succeeds with step output as result
    // -----------------------------------------------------------------------

    @Test
    void singleStepToolWorkflowSucceedsWithStepOutput() {
        EventBus eventBus = newEventBus();
        Map<String, Object> stepOutput = Map.of("file", "content");
        WorkflowRuntime runtime = runtimeWith(eventBus,
                stubExecutor(WorkflowStep.StepType.TOOL, stepOutput, null));
        try {
            runtime.declareWorkflow("read-and-invoice",
                    List.of(new WorkflowStep(WorkflowStep.StepType.TOOL, "read-file")));

            WorkflowInstanceView started = runtime.startWorkflow("read-and-invoice", Map.of());
            WorkflowResultView result = awaitResult(runtime, started.workflowId(), Duration.ofSeconds(5));

            assertEquals(WorkflowStatus.SUCCEEDED, result.status());
            assertNull(result.failureSummary());
            assertTrue(result.result() instanceof Map<?, ?>);
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result.result();
            assertEquals("content", resultMap.get("file"));
        } finally {
            runtime.close();
        }
    }

    // -----------------------------------------------------------------------
    // Scenario: multi-step ordered execution, previous output flows to next
    // -----------------------------------------------------------------------

    @Test
    void multiStepOrderedExecutionAndOutputPropagation() {
        EventBus eventBus = newEventBus();
        List<String> callOrder = new CopyOnWriteArrayList<>();
        List<Map<String, Object>> stepBInputs = new CopyOnWriteArrayList<>();

        WorkflowStepExecutor stepA = new WorkflowStepExecutor() {
            @Override
            public WorkflowStep.StepType stepType() {
                return WorkflowStep.StepType.TOOL;
            }

            @Override
            public WorkflowStepResult execute(WorkflowStep step, Map<String, Object> input) {
                callOrder.add("A");
                return WorkflowStepResult.success(Map.of("key", "value"));
            }
        };

        WorkflowStepExecutor stepB = new WorkflowStepExecutor() {
            @Override
            public WorkflowStep.StepType stepType() {
                return WorkflowStep.StepType.SERVICE;
            }

            @Override
            public WorkflowStepResult execute(WorkflowStep step, Map<String, Object> input) {
                callOrder.add("B");
                stepBInputs.add(input);
                return WorkflowStepResult.success(Map.of("invoice", "done"));
            }
        };

        WorkflowRuntime runtime = runtimeWith(eventBus, stepA, stepB);
        try {
            runtime.declareWorkflow("order-process", List.of(
                    new WorkflowStep(WorkflowStep.StepType.TOOL, "read-file"),
                    new WorkflowStep(WorkflowStep.StepType.SERVICE, "invoice-svc")));

            WorkflowInstanceView started = runtime.startWorkflow("order-process", Map.of());
            WorkflowResultView result = awaitResult(runtime, started.workflowId(), Duration.ofSeconds(5));

            assertEquals(WorkflowStatus.SUCCEEDED, result.status());
            assertEquals(List.of("A", "B"), callOrder);
            assertFalse(stepBInputs.isEmpty());
            assertEquals("value", stepBInputs.get(0).get("key"));
        } finally {
            runtime.close();
        }
    }

    // -----------------------------------------------------------------------
    // Scenario: step failure stops execution, workflow reaches FAILED
    // -----------------------------------------------------------------------

    @Test
    void stepFailurePropagatesAndStopsExecution() {
        EventBus eventBus = newEventBus();
        List<String> callOrder = new CopyOnWriteArrayList<>();

        WorkflowStepExecutor stepA = new WorkflowStepExecutor() {
            @Override
            public WorkflowStep.StepType stepType() {
                return WorkflowStep.StepType.TOOL;
            }

            @Override
            public WorkflowStepResult execute(WorkflowStep step, Map<String, Object> input) {
                callOrder.add("A");
                return WorkflowStepResult.failure("connection refused");
            }
        };

        WorkflowStepExecutor stepB = new WorkflowStepExecutor() {
            @Override
            public WorkflowStep.StepType stepType() {
                return WorkflowStep.StepType.SERVICE;
            }

            @Override
            public WorkflowStepResult execute(WorkflowStep step, Map<String, Object> input) {
                callOrder.add("B");
                return WorkflowStepResult.success(Map.of());
            }
        };

        WorkflowRuntime runtime = runtimeWith(eventBus, stepA, stepB);
        try {
            runtime.declareWorkflow("failing-workflow", List.of(
                    new WorkflowStep(WorkflowStep.StepType.TOOL, "step-a"),
                    new WorkflowStep(WorkflowStep.StepType.SERVICE, "step-b")));

            WorkflowInstanceView started = runtime.startWorkflow("failing-workflow", Map.of());
            WorkflowResultView result = awaitResult(runtime, started.workflowId(), Duration.ofSeconds(5));

            assertEquals(WorkflowStatus.FAILED, result.status());
            assertEquals(List.of("A"), callOrder, "step B must not be called after step A fails");
            assertNotNull(result.failureSummary());
            assertTrue(result.failureSummary().contains("step-a"),
                    "failure summary should identify the failed step");
            assertTrue(result.failureSummary().contains("connection refused"),
                    "failure summary should include the failure reason");
        } finally {
            runtime.close();
        }
    }

    // -----------------------------------------------------------------------
    // Scenario: no-step workflow succeeds without any executor invocation
    // -----------------------------------------------------------------------

    @Test
    void noStepWorkflowSucceedsWithoutInvokingExecutors() {
        EventBus eventBus = newEventBus();
        List<String> executorCalls = new CopyOnWriteArrayList<>();

        WorkflowStepExecutor executor = new WorkflowStepExecutor() {
            @Override
            public WorkflowStep.StepType stepType() {
                return WorkflowStep.StepType.TOOL;
            }

            @Override
            public WorkflowStepResult execute(WorkflowStep step, Map<String, Object> input) {
                executorCalls.add("called");
                return WorkflowStepResult.success(Map.of());
            }
        };

        WorkflowRuntime runtime = runtimeWith(eventBus, executor);
        try {
            runtime.declareWorkflow("empty-workflow");

            WorkflowInstanceView started = runtime.startWorkflow("empty-workflow", Map.of());
            WorkflowResultView result = awaitResult(runtime, started.workflowId(), Duration.ofSeconds(5));

            assertEquals(WorkflowStatus.SUCCEEDED, result.status());
            assertTrue(executorCalls.isEmpty(), "no executor should be called for a no-step workflow");
        } finally {
            runtime.close();
        }
    }

    // -----------------------------------------------------------------------
    // Scenario: blank step name causes declaration to fail (domain path)
    // -----------------------------------------------------------------------

    @Test
    void blankStepNameCausesDeclareWorkflowToFail() {
        EventBus eventBus = newEventBus();
        WorkflowRuntime runtime = runtimeWith(eventBus);
        try {
            assertThrows(IllegalArgumentException.class, () ->
                    runtime.declareWorkflow("bad-workflow",
                            List.of(new WorkflowStep(WorkflowStep.StepType.TOOL, "  "))));
        } finally {
            runtime.close();
        }
    }

    // -----------------------------------------------------------------------
    // Scenario: audit events for STARTED / COMPLETED / FAILED
    // -----------------------------------------------------------------------

    @Test
    void stepAuditEventsArePublishedOnSuccess() {
        EventBus eventBus = newEventBus();
        List<Event> stepEvents = new CopyOnWriteArrayList<>();
        eventBus.subscribe(EventType.WORKFLOW_STEP_STARTED, stepEvents::add);
        eventBus.subscribe(EventType.WORKFLOW_STEP_COMPLETED, stepEvents::add);

        WorkflowRuntime runtime = runtimeWith(eventBus,
                stubExecutor(WorkflowStep.StepType.TOOL, Map.of("out", "x"), null));
        try {
            runtime.declareWorkflow("audited-workflow",
                    List.of(new WorkflowStep(WorkflowStep.StepType.TOOL, "my-tool")));

            WorkflowInstanceView started = runtime.startWorkflow("audited-workflow", Map.of());
            awaitResult(runtime, started.workflowId(), Duration.ofSeconds(5));

            assertEquals(2, stepEvents.size());
            Event startedEvent = stepEvents.stream()
                    .filter(e -> e.type() == EventType.WORKFLOW_STEP_STARTED)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("WORKFLOW_STEP_STARTED not found"));
            assertEquals(started.workflowId(), startedEvent.metadata().get("workflowId"));
            assertEquals(0, startedEvent.metadata().get("stepIndex"));
            assertEquals("TOOL", startedEvent.metadata().get("stepType"));
            assertEquals("my-tool", startedEvent.metadata().get("stepName"));

            Event completedEvent = stepEvents.stream()
                    .filter(e -> e.type() == EventType.WORKFLOW_STEP_COMPLETED)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("WORKFLOW_STEP_COMPLETED not found"));
            assertEquals(started.workflowId(), completedEvent.metadata().get("workflowId"));
            assertEquals(0, completedEvent.metadata().get("stepIndex"));
            assertEquals("TOOL", completedEvent.metadata().get("stepType"));
            assertEquals("my-tool", completedEvent.metadata().get("stepName"));
        } finally {
            runtime.close();
        }
    }

    @Test
    void stepFailureAuditEventIsPublished() {
        EventBus eventBus = newEventBus();
        List<Event> stepFailedEvents = new CopyOnWriteArrayList<>();
        eventBus.subscribe(EventType.WORKFLOW_STEP_FAILED, stepFailedEvents::add);

        WorkflowRuntime runtime = runtimeWith(eventBus,
                failingExecutor(WorkflowStep.StepType.SERVICE, "timeout"));
        try {
            runtime.declareWorkflow("fail-audit-workflow",
                    List.of(new WorkflowStep(WorkflowStep.StepType.SERVICE, "remote-svc")));

            WorkflowInstanceView started = runtime.startWorkflow("fail-audit-workflow", Map.of());
            awaitResult(runtime, started.workflowId(), Duration.ofSeconds(5));

            assertEquals(1, stepFailedEvents.size());
            Event failedEvent = stepFailedEvents.get(0);
            assertEquals(started.workflowId(), failedEvent.metadata().get("workflowId"));
            assertEquals(0, failedEvent.metadata().get("stepIndex"));
            assertEquals("SERVICE", failedEvent.metadata().get("stepType"));
            assertEquals("remote-svc", failedEvent.metadata().get("stepName"));
            assertEquals("timeout", failedEvent.metadata().get("failureReason"));
        } finally {
            runtime.close();
        }
    }
}
