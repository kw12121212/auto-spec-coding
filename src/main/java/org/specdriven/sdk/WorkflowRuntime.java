package org.specdriven.sdk;

import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class WorkflowRuntime implements AutoCloseable {

    private static final Pattern CREATE_WORKFLOW_PATTERN = Pattern.compile(
            "(?is)^\\s*CREATE\\s+WORKFLOW\\s+(IF\\s+NOT\\s+EXISTS\\s+)?([a-zA-Z0-9_-]+)\\s*;?\\s*$");

    private final EventBus eventBus;
    private final Map<WorkflowStep.StepType, WorkflowStepExecutor> stepExecutors;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentMap<String, WorkflowDeclaration> declarations = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, WorkflowRecord> instances = new ConcurrentHashMap<>();

    WorkflowRuntime(EventBus eventBus, List<WorkflowStepExecutor> stepExecutors) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus must not be null");
        Map<WorkflowStep.StepType, WorkflowStepExecutor> map = new EnumMap<>(WorkflowStep.StepType.class);
        if (stepExecutors != null) {
            for (WorkflowStepExecutor exec : stepExecutors) {
                map.put(exec.stepType(), exec);
            }
        }
        this.stepExecutors = Map.copyOf(map);
    }

    void declareWorkflow(String workflowName, List<WorkflowStep> steps) {
        String normalizedName = normalizeWorkflowName(workflowName);
        declarations.put(normalizedName, new WorkflowDeclaration(normalizedName, copySteps(steps)));
        publishDeclared(normalizedName, "domain");
    }

    void declareWorkflow(String workflowName) {
        declareWorkflow(workflowName, List.of());
    }

    void declareWorkflowSql(String sql) {
        declareWorkflowSql(sql, List.of());
    }

    void declareWorkflowSql(String sql, List<WorkflowStep> steps) {
        Matcher matcher = CREATE_WORKFLOW_PATTERN.matcher(sql == null ? "" : sql);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unsupported workflow declaration SQL");
        }
        String normalizedName = normalizeWorkflowName(matcher.group(2));
        declarations.put(normalizedName, new WorkflowDeclaration(normalizedName, copySteps(steps)));
        publishDeclared(normalizedName, "sql");
    }

    WorkflowInstanceView startWorkflow(String workflowName, Map<String, Object> input) {
        String normalizedName = normalizeWorkflowName(workflowName);
        WorkflowDeclaration declaration = declarations.get(normalizedName);
        if (declaration == null) {
            throw new IllegalArgumentException("Workflow not declared: " + normalizedName);
        }
        long now = System.currentTimeMillis();
        String workflowId = UUID.randomUUID().toString();
        WorkflowRecord record = new WorkflowRecord(
                workflowId,
                declaration,
                now,
                new AtomicReference<>(WorkflowStatus.ACCEPTED),
                new AtomicReference<>(now),
                copyMap(input),
                new AtomicReference<>(null),
                new AtomicReference<>(null));
        instances.put(workflowId, record);
        WorkflowInstanceView acceptedView = record.instanceView();
        eventBus.publish(new Event(
                EventType.WORKFLOW_STARTED,
                now,
                workflowId,
                Map.of(
                        "workflowId", workflowId,
                        "workflowName", normalizedName,
                        "status", WorkflowStatus.ACCEPTED.name())));
        executor.submit(() -> advanceWorkflow(record));
        return acceptedView;
    }

    WorkflowInstanceView workflowState(String workflowId) {
        return requireWorkflow(workflowId).instanceView();
    }

    WorkflowResultView workflowResult(String workflowId) {
        return requireWorkflow(workflowId).resultView();
    }

    private void advanceWorkflow(WorkflowRecord record) {
        transition(record, WorkflowStatus.RUNNING);

        // Legacy test-control inputs (preserve existing behavior)
        if (Boolean.TRUE.equals(record.input().get("waitForInput"))) {
            transition(record, WorkflowStatus.WAITING_FOR_INPUT);
            return;
        }
        if (Boolean.TRUE.equals(record.input().get("fail"))) {
            fail(record, "Workflow failed due to requested failure");
            return;
        }

        List<WorkflowStep> steps = record.declaration().steps();

        if (steps.isEmpty()) {
            succeed(record, defaultResult(record));
            return;
        }

        Map<String, Object> stepInput = new LinkedHashMap<>(record.input());
        for (int i = 0; i < steps.size(); i++) {
            WorkflowStep step = steps.get(i);
            publishStepEvent(EventType.WORKFLOW_STEP_STARTED, record, i, step, null);

            WorkflowStepExecutor exec = stepExecutors.get(step.type());
            if (exec == null) {
                String reason = "No executor registered for step type: " + step.type() + " (step " + i + ": " + step.name() + ")";
                publishStepEvent(EventType.WORKFLOW_STEP_FAILED, record, i, step, reason);
                fail(record, reason);
                return;
            }

            WorkflowStepResult result;
            try {
                result = exec.execute(step, Map.copyOf(stepInput));
            } catch (Exception e) {
                String reason = "Step " + i + " (" + step.name() + ") threw exception: " + e.getMessage();
                publishStepEvent(EventType.WORKFLOW_STEP_FAILED, record, i, step, reason);
                fail(record, reason);
                return;
            }

            if (result.isFailure()) {
                String reason = "Step " + i + " (" + step.name() + ") failed: " + result.failureReason();
                publishStepEvent(EventType.WORKFLOW_STEP_FAILED, record, i, step, result.failureReason());
                fail(record, reason);
                return;
            }

            publishStepEvent(EventType.WORKFLOW_STEP_COMPLETED, record, i, step, null);
            stepInput.putAll(result.output());
        }

        succeed(record, Map.copyOf(stepInput));
    }

    private void publishStepEvent(EventType type, WorkflowRecord record, int stepIndex, WorkflowStep step, String failureReason) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("workflowId", record.workflowId());
        meta.put("stepIndex", stepIndex);
        meta.put("stepType", step.type().name());
        meta.put("stepName", step.name());
        if (failureReason != null) {
            meta.put("failureReason", failureReason);
        }
        eventBus.publish(new Event(type, System.currentTimeMillis(), record.workflowId(), Map.copyOf(meta)));
    }

    private void publishDeclared(String workflowName, String declarationPath) {
        eventBus.publish(new Event(
                EventType.WORKFLOW_DECLARED,
                System.currentTimeMillis(),
                workflowName,
                Map.of(
                        "workflowName", workflowName,
                        "declarationPath", declarationPath)));
    }

    private void transition(WorkflowRecord record, WorkflowStatus nextStatus) {
        WorkflowStatus previous = record.status().getAndSet(nextStatus);
        long now = System.currentTimeMillis();
        record.updatedAt().set(now);
        eventBus.publish(new Event(
                EventType.WORKFLOW_STATE_CHANGED,
                now,
                record.workflowId(),
                Map.of(
                        "workflowId", record.workflowId(),
                        "workflowName", record.workflowName(),
                        "fromStatus", previous.name(),
                        "toStatus", nextStatus.name())));
    }

    private void succeed(WorkflowRecord record, Object result) {
        record.result().set(result);
        transition(record, WorkflowStatus.SUCCEEDED);
        eventBus.publish(new Event(
                EventType.WORKFLOW_COMPLETED,
                System.currentTimeMillis(),
                record.workflowId(),
                Map.of(
                        "workflowId", record.workflowId(),
                        "workflowName", record.workflowName(),
                        "status", WorkflowStatus.SUCCEEDED.name())));
    }

    private void fail(WorkflowRecord record, String failureReason) {
        record.failureSummary().set(failureReason);
        transition(record, WorkflowStatus.FAILED);
        eventBus.publish(new Event(
                EventType.WORKFLOW_FAILED,
                System.currentTimeMillis(),
                record.workflowId(),
                Map.of(
                        "workflowId", record.workflowId(),
                        "workflowName", record.workflowName(),
                        "failureReason", failureReason)));
    }

    private WorkflowRecord requireWorkflow(String workflowId) {
        if (workflowId == null || workflowId.isBlank()) {
            throw new IllegalArgumentException("workflowId must not be blank");
        }
        WorkflowRecord record = instances.get(workflowId);
        if (record == null) {
            throw new IllegalArgumentException("Workflow instance not found: " + workflowId);
        }
        return record;
    }

    private static String normalizeWorkflowName(String workflowName) {
        if (workflowName == null || workflowName.isBlank()) {
            throw new IllegalArgumentException("workflowName must not be blank");
        }
        return workflowName.trim();
    }

    private static List<WorkflowStep> copySteps(List<WorkflowStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        return List.copyOf(steps);
    }

    private static Map<String, Object> copyMap(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(input));
    }

    private static Map<String, Object> defaultResult(WorkflowRecord record) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workflowName", record.workflowName());
        result.put("input", record.input());
        result.put("status", WorkflowStatus.SUCCEEDED.name());
        return Map.copyOf(result);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private record WorkflowDeclaration(String workflowName, List<WorkflowStep> steps) {}

    private record WorkflowRecord(
            String workflowId,
            WorkflowDeclaration declaration,
            long createdAt,
            AtomicReference<WorkflowStatus> status,
            AtomicReference<Long> updatedAt,
            Map<String, Object> input,
            AtomicReference<Object> result,
            AtomicReference<String> failureSummary) {

        String workflowName() {
            return declaration.workflowName();
        }

        WorkflowInstanceView instanceView() {
            return new WorkflowInstanceView(workflowId, workflowName(), status.get(), createdAt, updatedAt.get());
        }

        WorkflowResultView resultView() {
            return new WorkflowResultView(
                    workflowId,
                    workflowName(),
                    status.get(),
                    result.get(),
                    failureSummary.get(),
                    createdAt,
                    updatedAt.get());
        }
    }
}
