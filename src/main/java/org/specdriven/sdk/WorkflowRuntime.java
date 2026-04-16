package org.specdriven.sdk;

import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.json.JsonReader;
import org.specdriven.agent.json.JsonWriter;
import org.specdriven.agent.question.DeliveryMode;
import org.specdriven.agent.question.Question;
import org.specdriven.agent.question.QuestionCategory;
import org.specdriven.agent.question.QuestionDeliveryService;
import org.specdriven.agent.question.QuestionStatus;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class WorkflowRuntime implements AutoCloseable {

    private static final Pattern CREATE_WORKFLOW_PATTERN = Pattern.compile(
            "(?is)^\\s*CREATE\\s+WORKFLOW\\s+(IF\\s+NOT\\s+EXISTS\\s+)?([a-zA-Z0-9_-]+)\\s*;?\\s*$");
    private static final int MAX_STEP_ATTEMPTS = 2;

    private final EventBus eventBus;
    private final Map<WorkflowStep.StepType, WorkflowStepExecutor> stepExecutors;
    private final Supplier<QuestionDeliveryService> questionDeliveryServiceSupplier;
    private final WorkflowStateStore stateStore;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentMap<String, WorkflowDeclaration> declarations = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, WorkflowRecord> instances = new ConcurrentHashMap<>();
    private final Consumer<Event> questionAnsweredListener;
    private final AtomicBoolean recovered = new AtomicBoolean();

    WorkflowRuntime(EventBus eventBus, List<WorkflowStepExecutor> stepExecutors) {
        this(eventBus, stepExecutors, () -> null, new InMemoryStateStore());
    }

    WorkflowRuntime(EventBus eventBus, List<WorkflowStepExecutor> stepExecutors,
                    QuestionDeliveryService questionDeliveryService) {
        this(eventBus, stepExecutors, () -> questionDeliveryService, new InMemoryStateStore());
    }

    WorkflowRuntime(EventBus eventBus, List<WorkflowStepExecutor> stepExecutors,
                    QuestionDeliveryService questionDeliveryService,
                    WorkflowStateStore stateStore) {
        this(eventBus, stepExecutors, () -> questionDeliveryService, stateStore);
    }

    WorkflowRuntime(EventBus eventBus, List<WorkflowStepExecutor> stepExecutors,
                    Supplier<QuestionDeliveryService> questionDeliveryServiceSupplier,
                    WorkflowStateStore stateStore) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus must not be null");
        Map<WorkflowStep.StepType, WorkflowStepExecutor> map = new EnumMap<>(WorkflowStep.StepType.class);
        if (stepExecutors != null) {
            for (WorkflowStepExecutor exec : stepExecutors) {
                map.put(exec.stepType(), exec);
            }
        }
        this.stepExecutors = Map.copyOf(map);
        this.questionDeliveryServiceSupplier = Objects.requireNonNull(questionDeliveryServiceSupplier,
                "questionDeliveryServiceSupplier must not be null");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore must not be null");
        this.questionAnsweredListener = this::onQuestionAnswered;
        eventBus.subscribe(EventType.QUESTION_ANSWERED, this.questionAnsweredListener);
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
        ensureRecovered();
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
                WorkflowStatus.ACCEPTED,
                now,
                mutableCopyMap(input),
                null,
                null,
                null,
                null,
                false,
                0,
                1,
                null,
                null);
        instances.put(workflowId, record);
        persist(record);
        publishCheckpointSaved(record);
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
        ensureRecovered();
        return requireWorkflow(workflowId).instanceView();
    }

    WorkflowResultView workflowResult(String workflowId) {
        ensureRecovered();
        return requireWorkflow(workflowId).resultView();
    }

    private void onQuestionAnswered(Event event) {
        Object sessionIdObj = event.metadata().get("sessionId");
        if (!(sessionIdObj instanceof String sessionId) || sessionId.isBlank()) {
            return;
        }
        WorkflowRecord record = instances.get(sessionId);
        if (record == null) {
            return;
        }
        Object contentObj = event.metadata().get("content");
        String content = (contentObj instanceof String s) ? s : "";
        String workflowId = record.workflowId();
        String questionId;
        synchronized (record) {
            if (record.status != WorkflowStatus.WAITING_FOR_INPUT) {
                return;
            }
            questionId = record.waitingQuestionId;
            record.context = mutableCopyMap(record.context);
            record.context.put("humanInput", content);
            record.waitingQuestionId = null;
            record.waitingPrompt = null;
            record.attemptNumber = 1;
        }
        transition(record, WorkflowStatus.RUNNING);
        persist(record);
        publishCheckpointSaved(record);
        eventBus.publish(new Event(
                EventType.WORKFLOW_RESUMED,
                System.currentTimeMillis(),
                workflowId,
                Map.of(
                        "workflowId", workflowId,
                        "questionId", questionId == null ? "" : questionId)));
        executor.submit(() -> advanceWorkflow(record));
    }

    private void advanceWorkflow(WorkflowRecord record) {
        WorkflowStatus initialStatus;
        synchronized (record) {
            initialStatus = record.status;
        }
        if (initialStatus == WorkflowStatus.ACCEPTED) {
            transition(record, WorkflowStatus.RUNNING);
        } else if (initialStatus == WorkflowStatus.FAILED
                || initialStatus == WorkflowStatus.SUCCEEDED
                || initialStatus == WorkflowStatus.CANCELLED) {
            return;
        }

        // Legacy test-control inputs (preserve existing behavior)
        if (record.nextStepIndex() == 0 && Boolean.TRUE.equals(record.contextValue("waitForInput"))) {
            transition(record, WorkflowStatus.WAITING_FOR_INPUT);
            persist(record);
            publishCheckpointSaved(record);
            return;
        }
        if (record.nextStepIndex() == 0 && Boolean.TRUE.equals(record.contextValue("fail"))) {
            fail(record, "Workflow failed due to requested failure");
            return;
        }

        List<WorkflowStep> steps = record.declaration.steps();

        if (steps.isEmpty()) {
            succeed(record, defaultResult(record));
            return;
        }

        Map<String, Object> stepInput = record.contextCopy();
        for (int i = record.nextStepIndex(); i < steps.size(); ) {
            WorkflowStep step = steps.get(i);
            int attemptNumber = checkpointStepBoundary(record, i, stepInput);
            publishStepEvent(EventType.WORKFLOW_STEP_STARTED, record, i, step, null);

            WorkflowStepExecutor exec = stepExecutors.get(step.type());
            if (exec == null) {
                String reason = "No executor registered for step type: " + step.type() + " (step " + i + ": " + step.name() + ")";
                publishStepEvent(EventType.WORKFLOW_STEP_FAILED, record, i, step, reason);
                failStep(record, step, reason, false);
                return;
            }

            WorkflowStepResult result;
            try {
                result = exec.execute(step, Map.copyOf(stepInput));
            } catch (Exception e) {
                String message = e.getMessage();
                String reason = (message == null || message.isBlank())
                        ? e.getClass().getSimpleName()
                        : message;
                publishStepEvent(EventType.WORKFLOW_STEP_FAILED, record, i, step, reason);
                failStep(record, step, reason, false);
                return;
            }

            if (result.isFailure()) {
                publishStepEvent(EventType.WORKFLOW_STEP_FAILED, record, i, step, result.failureReason());
                if (result.isRetryableFailure() && attemptNumber < MAX_STEP_ATTEMPTS) {
                    synchronized (record) {
                        record.attemptNumber = attemptNumber + 1;
                        record.lastFailureReason = result.failureReason();
                        record.failedStepName = step.name();
                        record.retryExhausted = false;
                        record.updatedAt = System.currentTimeMillis();
                    }
                    persist(record);
                    publishCheckpointSaved(record);
                    eventBus.publish(new Event(
                            EventType.WORKFLOW_STEP_RETRY_SCHEDULED,
                            System.currentTimeMillis(),
                            record.workflowId(),
                            Map.of(
                                    "workflowId", record.workflowId(),
                                    "workflowName", record.workflowName(),
                                    "stepIndex", i,
                                    "stepName", step.name(),
                                    "attemptNumber", attemptNumber + 1,
                                    "failureReason", result.failureReason())));
                    continue;
                }
                failStep(record, step, result.failureReason(), result.isRetryableFailure());
                return;
            }

            if (result.isAwaitingInput()) {
                QuestionDeliveryService questionDeliveryService = questionDeliveryServiceSupplier.get();
                if (questionDeliveryService == null) {
                    fail(record, "no question delivery surface configured");
                    return;
                }
                String workflowId = record.workflowId();
                String prompt = result.inputPrompt();
                String questionId = UUID.randomUUID().toString();
                Question question = new Question(
                        questionId,
                        workflowId,
                        prompt,
                        "Workflow '" + record.workflowName() + "' is paused pending human input.",
                        "Provide a response to resume the workflow.",
                        QuestionStatus.WAITING_FOR_ANSWER,
                        QuestionCategory.PERMISSION_CONFIRMATION,
                        DeliveryMode.PAUSE_WAIT_HUMAN);
                try {
                    questionDeliveryService.deliver(question);
                } catch (Exception e) {
                    String message = e.getMessage();
                    fail(record, "Failed to deliver workflow question: "
                            + ((message == null || message.isBlank()) ? e.getClass().getSimpleName() : message));
                    return;
                }
                eventBus.publish(new Event(
                        EventType.WORKFLOW_PAUSED_FOR_INPUT,
                        System.currentTimeMillis(),
                        workflowId,
                        Map.of("workflowId", workflowId, "questionId", questionId, "prompt", prompt)));
                transition(record, WorkflowStatus.WAITING_FOR_INPUT);
                synchronized (record) {
                    record.nextStepIndex = i + 1;
                    record.attemptNumber = 1;
                    record.context = mutableCopyMap(stepInput);
                    record.waitingQuestionId = questionId;
                    record.waitingPrompt = prompt;
                    record.updatedAt = System.currentTimeMillis();
                }
                persist(record);
                publishCheckpointSaved(record);
                return;
            }

            publishStepEvent(EventType.WORKFLOW_STEP_COMPLETED, record, i, step, null);
            stepInput.putAll(result.output());
            synchronized (record) {
                record.context = mutableCopyMap(stepInput);
                record.nextStepIndex = i + 1;
                record.attemptNumber = 1;
                record.lastFailureReason = null;
                record.failedStepName = null;
                record.retryExhausted = false;
                record.updatedAt = System.currentTimeMillis();
            }
            i++;
        }

        succeed(record, Map.copyOf(stepInput));
    }

    private int checkpointStepBoundary(WorkflowRecord record, int stepIndex, Map<String, Object> stepInput) {
        int attemptNumber;
        synchronized (record) {
            attemptNumber = record.attemptNumber;
            record.status = WorkflowStatus.RUNNING;
            record.nextStepIndex = stepIndex;
            record.context = mutableCopyMap(stepInput);
            record.waitingQuestionId = null;
            record.waitingPrompt = null;
            record.updatedAt = System.currentTimeMillis();
        }
        persist(record);
        publishCheckpointSaved(record);
        return attemptNumber;
    }

    private void publishStepEvent(EventType type, WorkflowRecord record, int stepIndex, WorkflowStep step, String failureReason) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("workflowId", record.workflowId());
        meta.put("workflowName", record.workflowName());
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
        WorkflowStatus previous;
        long now;
        synchronized (record) {
            previous = record.status;
            if (previous == nextStatus) {
                return;
            }
            now = System.currentTimeMillis();
            record.status = nextStatus;
            record.updatedAt = now;
        }
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
        Map<String, Object> resultMap = result instanceof Map<?, ?> map
                ? copyMap(castStringObjectMap(map))
                : Map.of();
        synchronized (record) {
            record.result = resultMap;
            record.failureSummary = null;
            record.lastFailureReason = null;
            record.failedStepName = null;
            record.retryExhausted = false;
            record.nextStepIndex = record.declaration.steps().size();
            record.attemptNumber = 1;
        }
        transition(record, WorkflowStatus.SUCCEEDED);
        persist(record);
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
        synchronized (record) {
            record.failureSummary = failureReason;
            record.lastFailureReason = failureReason;
            record.failedStepName = null;
            record.retryExhausted = false;
            record.result = null;
            record.waitingQuestionId = null;
            record.waitingPrompt = null;
        }
        transition(record, WorkflowStatus.FAILED);
        persist(record);
        eventBus.publish(new Event(
                EventType.WORKFLOW_FAILED,
                System.currentTimeMillis(),
                record.workflowId(),
                Map.of(
                        "workflowId", record.workflowId(),
                        "workflowName", record.workflowName(),
                        "failureReason", failureReason)));
    }

    private void failStep(WorkflowRecord record, WorkflowStep step, String failureReason, boolean retryExhausted) {
        synchronized (record) {
            record.failureSummary = buildStepFailureSummary(step.name(), failureReason, retryExhausted);
            record.lastFailureReason = failureReason;
            record.failedStepName = step.name();
            record.retryExhausted = retryExhausted;
            record.result = null;
            record.waitingQuestionId = null;
            record.waitingPrompt = null;
        }
        transition(record, WorkflowStatus.FAILED);
        persist(record);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("workflowId", record.workflowId());
        metadata.put("workflowName", record.workflowName());
        metadata.put("failureReason", failureReason);
        if (retryExhausted) {
            metadata.put("retryExhausted", true);
            metadata.put("failedStepName", step.name());
        }
        eventBus.publish(new Event(
                EventType.WORKFLOW_FAILED,
                System.currentTimeMillis(),
                record.workflowId(),
                Map.copyOf(metadata)));
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

    private static Map<String, Object> mutableCopyMap(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(input);
    }

    private static Map<String, Object> defaultResult(WorkflowRecord record) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workflowName", record.workflowName());
        result.put("input", record.contextCopy());
        result.put("status", WorkflowStatus.SUCCEEDED.name());
        return Map.copyOf(result);
    }

    private void recoverPersistedInstances() {
        for (StoredWorkflowRecord stored : stateStore.loadAll()) {
            WorkflowRecord record = new WorkflowRecord(stored);
            WorkflowRecord existing = instances.putIfAbsent(record.workflowId(), record);
            if (existing != null) {
                continue;
            }
            if (!isRecoverable(record.status())) {
                continue;
            }
            eventBus.publish(new Event(
                    EventType.WORKFLOW_RECOVERED,
                    System.currentTimeMillis(),
                    record.workflowId(),
                    Map.of(
                            "workflowId", record.workflowId(),
                            "workflowName", record.workflowName(),
                            "status", record.status().name(),
                            "resumeFromStepIndex", record.nextStepIndex())));
            if (record.status() == WorkflowStatus.ACCEPTED || record.status() == WorkflowStatus.RUNNING) {
                executor.submit(() -> advanceWorkflow(record));
            }
        }
    }

    private void ensureRecovered() {
        if (recovered.compareAndSet(false, true)) {
            recoverPersistedInstances();
        }
    }

    private void persist(WorkflowRecord record) {
        stateStore.save(record.snapshot());
    }

    private void publishCheckpointSaved(WorkflowRecord record) {
        StoredWorkflowRecord snapshot = record.snapshot();
        if (!isRecoverable(snapshot.status())) {
            return;
        }
        eventBus.publish(new Event(
                EventType.WORKFLOW_CHECKPOINT_SAVED,
                System.currentTimeMillis(),
                snapshot.workflowId(),
                Map.of(
                        "workflowId", snapshot.workflowId(),
                        "workflowName", snapshot.workflowName(),
                        "status", snapshot.status().name(),
                        "resumeFromStepIndex", snapshot.nextStepIndex())));
    }

    private static boolean isRecoverable(WorkflowStatus status) {
        return status == WorkflowStatus.ACCEPTED
                || status == WorkflowStatus.RUNNING
                || status == WorkflowStatus.WAITING_FOR_INPUT;
    }

    private static String buildStepFailureSummary(String stepName, String failureReason, boolean retryExhausted) {
        if (retryExhausted) {
            return "Step " + stepName + " failed after retry exhaustion: " + failureReason;
        }
        return "Step " + stepName + " failed: " + failureReason;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castStringObjectMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    @Override
    public void close() {
        eventBus.unsubscribe(EventType.QUESTION_ANSWERED, questionAnsweredListener);
        executor.shutdownNow();
        try {
            stateStore.close();
        } catch (Exception ignored) {
        }
    }

    private record WorkflowDeclaration(String workflowName, List<WorkflowStep> steps) {}

    interface WorkflowStateStore extends AutoCloseable {

        void save(StoredWorkflowRecord record);

        List<StoredWorkflowRecord> loadAll();

        @Override
        default void close() {}
    }

    static final class InMemoryStateStore implements WorkflowStateStore {

        private final ConcurrentMap<String, StoredWorkflowRecord> records = new ConcurrentHashMap<>();

        @Override
        public void save(StoredWorkflowRecord record) {
            records.put(record.workflowId(), record);
        }

        @Override
        public List<StoredWorkflowRecord> loadAll() {
            return Collections.unmodifiableList(new ArrayList<>(records.values()));
        }
    }

    static final class JdbcStateStore implements WorkflowStateStore {

        private static final String CREATE_TABLE = """
                CREATE TABLE IF NOT EXISTS workflow_runtime_state_v2 (
                    workflow_id   VARCHAR(64)  PRIMARY KEY,
                    workflow_name VARCHAR(255) NOT NULL,
                    status        VARCHAR(32)  NOT NULL,
                    created_at    BIGINT       NOT NULL,
                    updated_at    BIGINT       NOT NULL,
                    state_json    VARCHAR(20000) NOT NULL
                )
                """;
        private static final String TABLE_NAME = "workflow_runtime_state_v2";

        private final String jdbcUrl;
        private final AtomicBoolean initialized = new AtomicBoolean();
        private volatile boolean disabled;

        JdbcStateStore(String jdbcUrl) {
            this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl must not be null");
        }

        @Override
        public void save(StoredWorkflowRecord record) {
            ensureInitialized();
            if (disabled) {
                return;
            }
            String updateSql = "UPDATE " + TABLE_NAME + " SET workflow_name = ?, status = ?, created_at = ?, updated_at = ?, state_json = ? WHERE workflow_id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement update = conn.prepareStatement(updateSql)) {
                update.setString(1, record.workflowName());
                update.setString(2, record.status().name());
                update.setLong(3, record.createdAt());
                update.setLong(4, record.updatedAt());
                update.setString(5, JsonWriter.fromMap(record.toMap()));
                update.setString(6, record.workflowId());
                if (update.executeUpdate() == 0) {
                    String insertSql = "INSERT INTO " + TABLE_NAME + " (workflow_id, workflow_name, status, created_at, updated_at, state_json) VALUES (?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement insert = conn.prepareStatement(insertSql)) {
                        insert.setString(1, record.workflowId());
                        insert.setString(2, record.workflowName());
                        insert.setString(3, record.status().name());
                        insert.setLong(4, record.createdAt());
                        insert.setLong(5, record.updatedAt());
                        insert.setString(6, JsonWriter.fromMap(record.toMap()));
                        insert.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to persist workflow state for " + record.workflowId(), e);
            }
        }

        @Override
        public List<StoredWorkflowRecord> loadAll() {
            ensureInitialized();
            if (disabled) {
                return List.of();
            }
            String sql = "SELECT state_json FROM " + TABLE_NAME + " ORDER BY created_at ASC";
            List<StoredWorkflowRecord> results = new ArrayList<>();
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    results.add(StoredWorkflowRecord.fromMap(JsonReader.parseObject(rs.getString(1))));
                }
                return Collections.unmodifiableList(results);
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to load workflow runtime state", e);
            }
        }

        private void ensureInitialized() {
            if (initialized.compareAndSet(false, true)) {
                try {
                    initTable();
                } catch (RuntimeException e) {
                    disabled = true;
                }
            }
        }

        private void initTable() {
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(CREATE_TABLE);
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to initialize workflow_runtime_state table", e);
            }
        }

        private Connection getConnection() throws SQLException {
            return DriverManager.getConnection(jdbcUrl, "root", "");
        }
    }

    private static final class WorkflowRecord {

        private final String workflowId;
        private final WorkflowDeclaration declaration;
        private final long createdAt;
        private WorkflowStatus status;
        private long updatedAt;
        private Map<String, Object> context;
        private Map<String, Object> result;
        private String failureSummary;
        private String failedStepName;
        private String lastFailureReason;
        private boolean retryExhausted;
        private int nextStepIndex;
        private int attemptNumber;
        private String waitingQuestionId;
        private String waitingPrompt;

        private WorkflowRecord(String workflowId,
                               WorkflowDeclaration declaration,
                               long createdAt,
                               WorkflowStatus status,
                               long updatedAt,
                               Map<String, Object> context,
                               Map<String, Object> result,
                               String failureSummary,
                               String failedStepName,
                               String lastFailureReason,
                               boolean retryExhausted,
                               int nextStepIndex,
                               int attemptNumber,
                               String waitingQuestionId,
                               String waitingPrompt) {
            this.workflowId = workflowId;
            this.declaration = declaration;
            this.createdAt = createdAt;
            this.status = status;
            this.updatedAt = updatedAt;
            this.context = mutableCopyMap(context);
            this.result = result == null ? null : copyMap(result);
            this.failureSummary = failureSummary;
            this.failedStepName = failedStepName;
            this.lastFailureReason = lastFailureReason;
            this.retryExhausted = retryExhausted;
            this.nextStepIndex = nextStepIndex;
            this.attemptNumber = attemptNumber;
            this.waitingQuestionId = waitingQuestionId;
            this.waitingPrompt = waitingPrompt;
        }

        private WorkflowRecord(StoredWorkflowRecord stored) {
            this(
                    stored.workflowId(),
                    new WorkflowDeclaration(stored.workflowName(), stored.steps()),
                    stored.createdAt(),
                    stored.status(),
                    stored.updatedAt(),
                    stored.context(),
                    stored.result(),
                    stored.failureSummary(),
                    stored.failedStepName(),
                    stored.lastFailureReason(),
                    stored.retryExhausted(),
                    stored.nextStepIndex(),
                    stored.attemptNumber(),
                    stored.waitingQuestionId(),
                    stored.waitingPrompt());
        }

        private String workflowId() {
            return workflowId;
        }

        private String workflowName() {
            return declaration.workflowName();
        }

        private synchronized WorkflowStatus status() {
            return status;
        }

        private synchronized int nextStepIndex() {
            return nextStepIndex;
        }

        private synchronized Map<String, Object> contextCopy() {
            return mutableCopyMap(context);
        }

        private synchronized Object contextValue(String key) {
            return context.get(key);
        }

        private synchronized WorkflowInstanceView instanceView() {
            return new WorkflowInstanceView(workflowId, workflowName(), status, createdAt, updatedAt);
        }

        private synchronized WorkflowResultView resultView() {
            return new WorkflowResultView(
                    workflowId,
                    workflowName(),
                    status,
                    result,
                    failureSummary,
                    createdAt,
                    updatedAt);
        }

        private synchronized StoredWorkflowRecord snapshot() {
            return new StoredWorkflowRecord(
                    workflowId,
                    workflowName(),
                    declaration.steps(),
                    createdAt,
                    updatedAt,
                    status,
                    copyMap(context),
                    result == null ? null : copyMap(result),
                    failureSummary,
                    failedStepName,
                    lastFailureReason,
                    retryExhausted,
                    nextStepIndex,
                    attemptNumber,
                    waitingQuestionId,
                    waitingPrompt);
        }
    }

    private record StoredWorkflowRecord(
            String workflowId,
            String workflowName,
            List<WorkflowStep> steps,
            long createdAt,
            long updatedAt,
            WorkflowStatus status,
            Map<String, Object> context,
            Map<String, Object> result,
            String failureSummary,
            String failedStepName,
            String lastFailureReason,
            boolean retryExhausted,
            int nextStepIndex,
            int attemptNumber,
            String waitingQuestionId,
            String waitingPrompt) {

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("workflowId", workflowId);
            map.put("workflowName", workflowName);
            map.put("steps", steps.stream().map(step -> Map.<String, Object>of(
                    "type", step.type().name(),
                    "name", step.name())).toList());
            map.put("createdAt", createdAt);
            map.put("updatedAt", updatedAt);
            map.put("status", status.name());
            map.put("context", context == null ? Map.of() : context);
            map.put("result", result);
            map.put("failureSummary", failureSummary);
            map.put("failedStepName", failedStepName);
            map.put("lastFailureReason", lastFailureReason);
            map.put("retryExhausted", retryExhausted);
            map.put("nextStepIndex", nextStepIndex);
            map.put("attemptNumber", attemptNumber);
            map.put("waitingQuestionId", waitingQuestionId);
            map.put("waitingPrompt", waitingPrompt);
            return map;
        }

        @SuppressWarnings("unchecked")
        private static StoredWorkflowRecord fromMap(Map<String, Object> map) {
            List<WorkflowStep> steps = new ArrayList<>();
            Object stepsValue = map.get("steps");
            if (stepsValue instanceof List<?> list) {
                for (Object entry : list) {
                    if (entry instanceof Map<?, ?> stepMap) {
                        String type = String.valueOf(stepMap.get("type"));
                        String name = String.valueOf(stepMap.get("name"));
                        steps.add(new WorkflowStep(WorkflowStep.StepType.valueOf(type), name));
                    }
                }
            }
            Object contextValue = map.get("context");
            Object resultValue = map.get("result");
            return new StoredWorkflowRecord(
                    String.valueOf(map.get("workflowId")),
                    String.valueOf(map.get("workflowName")),
                    List.copyOf(steps),
                    asLong(map.get("createdAt")),
                    asLong(map.get("updatedAt")),
                    WorkflowStatus.valueOf(String.valueOf(map.get("status"))),
                    contextValue instanceof Map<?, ?> contextMap ? (Map<String, Object>) contextMap : Map.of(),
                    resultValue instanceof Map<?, ?> resultMap ? (Map<String, Object>) resultMap : null,
                    stringOrNull(map.get("failureSummary")),
                    stringOrNull(map.get("failedStepName")),
                    stringOrNull(map.get("lastFailureReason")),
                    Boolean.TRUE.equals(map.get("retryExhausted")),
                    asInt(map.get("nextStepIndex"), 0),
                    asInt(map.get("attemptNumber"), 1),
                    stringOrNull(map.get("waitingQuestionId")),
                    stringOrNull(map.get("waitingPrompt")));
        }

        private static long asLong(Object value) {
            return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
        }

        private static int asInt(Object value, int defaultValue) {
            if (value == null) {
                return defaultValue;
            }
            return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
        }

        private static String stringOrNull(Object value) {
            return value == null ? null : String.valueOf(value);
        }
    }
}
