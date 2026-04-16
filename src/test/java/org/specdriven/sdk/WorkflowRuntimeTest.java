package org.specdriven.sdk;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.EventType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

            assertTrue(received.contains(EventType.WORKFLOW_DECLARED));
            assertTrue(received.contains(EventType.WORKFLOW_STARTED));
            assertTrue(received.contains(EventType.WORKFLOW_STATE_CHANGED));
            assertTrue(received.contains(EventType.WORKFLOW_COMPLETED));
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

    private void sleepBriefly() {
        try {
            Thread.sleep(10L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted while waiting for workflow state");
        }
    }
}
