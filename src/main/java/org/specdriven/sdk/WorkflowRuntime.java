package org.specdriven.sdk;

import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;

import java.util.LinkedHashMap;
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
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentMap<String, String> declarations = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, WorkflowRecord> instances = new ConcurrentHashMap<>();

    WorkflowRuntime(EventBus eventBus) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus must not be null");
    }

    void declareWorkflow(String workflowName) {
        String normalizedName = normalizeWorkflowName(workflowName);
        declarations.put(normalizedName, normalizedName);
        publishDeclared(normalizedName, "domain");
    }

    void declareWorkflowSql(String sql) {
        Matcher matcher = CREATE_WORKFLOW_PATTERN.matcher(sql == null ? "" : sql);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unsupported workflow declaration SQL");
        }
        String normalizedName = normalizeWorkflowName(matcher.group(2));
        declarations.put(normalizedName, normalizedName);
        publishDeclared(normalizedName, "sql");
    }

    WorkflowInstanceView startWorkflow(String workflowName, Map<String, Object> input) {
        String normalizedName = normalizeWorkflowName(workflowName);
        if (!declarations.containsKey(normalizedName)) {
            throw new IllegalArgumentException("Workflow not declared: " + normalizedName);
        }
        long now = System.currentTimeMillis();
        String workflowId = UUID.randomUUID().toString();
        WorkflowRecord record = new WorkflowRecord(
                workflowId,
                normalizedName,
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
        if (Boolean.TRUE.equals(record.input().get("waitForInput"))) {
            transition(record, WorkflowStatus.WAITING_FOR_INPUT);
            return;
        }
        if (Boolean.TRUE.equals(record.input().get("fail"))) {
            fail(record, "Workflow failed due to requested failure");
            return;
        }
        succeed(record, defaultResult(record));
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

    private record WorkflowRecord(
            String workflowId,
            String workflowName,
            long createdAt,
            AtomicReference<WorkflowStatus> status,
            AtomicReference<Long> updatedAt,
            Map<String, Object> input,
            AtomicReference<Object> result,
            AtomicReference<String> failureSummary) {

        WorkflowInstanceView instanceView() {
            return new WorkflowInstanceView(workflowId, workflowName, status.get(), createdAt, updatedAt.get());
        }

        WorkflowResultView resultView() {
            return new WorkflowResultView(
                    workflowId,
                    workflowName,
                    status.get(),
                    result.get(),
                    failureSummary.get(),
                    createdAt,
                    updatedAt.get());
        }
    }
}
