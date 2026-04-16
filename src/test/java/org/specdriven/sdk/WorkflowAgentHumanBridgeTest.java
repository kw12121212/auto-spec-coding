package org.specdriven.sdk;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.event.SimpleEventBus;
import org.specdriven.agent.question.Answer;
import org.specdriven.agent.question.LoggingDeliveryChannel;
import org.specdriven.agent.question.Question;
import org.specdriven.agent.question.QuestionDeliveryChannel;
import org.specdriven.agent.question.QuestionDeliveryService;
import org.specdriven.agent.question.QuestionReplyCollector;
import org.specdriven.agent.question.QuestionRuntime;
import org.specdriven.agent.question.QuestionStatus;
import org.specdriven.agent.question.QuestionStore;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowAgentHumanBridgeTest {

    // -----------------------------------------------------------------------
    // WorkflowStepResult awaiting-input variant
    // -----------------------------------------------------------------------

    @Test
    void awaitingInputResult_isDistinguishableFromSuccessAndFailure() {
        WorkflowStepResult r = WorkflowStepResult.awaitingInput("Please confirm the invoice amount");
        assertTrue(r.isAwaitingInput());
        assertFalse(r.isFailure());
        assertEquals("Please confirm the invoice amount", r.inputPrompt());
    }

    @Test
    void successResult_isNotAwaitingInput() {
        WorkflowStepResult r = WorkflowStepResult.success(Map.of("k", "v"));
        assertFalse(r.isAwaitingInput());
    }

    @Test
    void failureResult_isNotAwaitingInput() {
        WorkflowStepResult r = WorkflowStepResult.failure("something went wrong");
        assertFalse(r.isAwaitingInput());
    }

    // -----------------------------------------------------------------------
    // Pause contract
    // -----------------------------------------------------------------------

    @Test
    void awaitingInputStep_transitionsWorkflowToWaitingForInput() throws Exception {
        SimpleEventBus eventBus = new SimpleEventBus();
        QuestionDeliveryService qds = stubDeliveryService(eventBus);
        WorkflowRuntime runtime = new WorkflowRuntime(eventBus,
                List.of(awaitingInputExecutor("Please confirm")), qds);
        try {
            runtime.declareWorkflow("approval-wf",
                    List.of(new WorkflowStep(WorkflowStep.StepType.TOOL, "confirm-step")));

            WorkflowInstanceView started = runtime.startWorkflow("approval-wf", Map.of());

            WorkflowInstanceView waiting = awaitStatus(runtime, started.workflowId(),
                    WorkflowStatus.WAITING_FOR_INPUT, Duration.ofSeconds(5));

            assertEquals(WorkflowStatus.WAITING_FOR_INPUT, waiting.status());
        } finally {
            runtime.close();
        }
    }

    @Test
    void awaitingInputStep_publishesPausedEventWithRequiredFields() throws Exception {
        SimpleEventBus eventBus = new SimpleEventBus();
        List<Event> pauseEvents = new CopyOnWriteArrayList<>();
        eventBus.subscribe(EventType.WORKFLOW_PAUSED_FOR_INPUT, pauseEvents::add);

        QuestionDeliveryService qds = stubDeliveryService(eventBus);
        WorkflowRuntime runtime = new WorkflowRuntime(eventBus,
                List.of(awaitingInputExecutor("Confirm the invoice")), qds);
        try {
            runtime.declareWorkflow("pause-event-wf",
                    List.of(new WorkflowStep(WorkflowStep.StepType.TOOL, "confirm-step")));

            WorkflowInstanceView started = runtime.startWorkflow("pause-event-wf", Map.of());
            awaitStatus(runtime, started.workflowId(), WorkflowStatus.WAITING_FOR_INPUT, Duration.ofSeconds(5));

            assertFalse(pauseEvents.isEmpty(), "WORKFLOW_PAUSED_FOR_INPUT must be published");
            Event pauseEvent = pauseEvents.get(0);
            assertEquals(started.workflowId(), pauseEvent.metadata().get("workflowId"));
            assertNotNull(pauseEvent.metadata().get("questionId"),
                    "questionId must be present in WORKFLOW_PAUSED_FOR_INPUT event");
            assertEquals("Confirm the invoice", pauseEvent.metadata().get("prompt"));
        } finally {
            runtime.close();
        }
    }

    @Test
    void awaitingInputWithoutDeliveryService_transitionsToFailed() throws Exception {
        SimpleEventBus eventBus = new SimpleEventBus();
        WorkflowRuntime runtime = new WorkflowRuntime(eventBus,
                List.of(awaitingInputExecutor("Confirm please")));
        try {
            runtime.declareWorkflow("no-qds-wf",
                    List.of(new WorkflowStep(WorkflowStep.StepType.TOOL, "confirm-step")));

            WorkflowInstanceView started = runtime.startWorkflow("no-qds-wf", Map.of());
            WorkflowResultView result = awaitResult(runtime, started.workflowId(), Duration.ofSeconds(5));

            assertEquals(WorkflowStatus.FAILED, result.status());
            assertNotNull(result.failureSummary());
            assertTrue(result.failureSummary().contains("no question delivery surface configured"),
                    "failure summary must indicate missing delivery surface; got: " + result.failureSummary());
        } finally {
            runtime.close();
        }
    }

    // -----------------------------------------------------------------------
    // Resume contract
    // -----------------------------------------------------------------------

    @Test
    void questionAnsweredEvent_resumesWorkflowToRunning() throws Exception {
        SimpleEventBus eventBus = new SimpleEventBus();
        List<Event> resumeEvents = new CopyOnWriteArrayList<>();
        eventBus.subscribe(EventType.WORKFLOW_RESUMED, resumeEvents::add);

        QuestionDeliveryService qds = stubDeliveryService(eventBus);
        WorkflowRuntime runtime = new WorkflowRuntime(eventBus,
                List.of(awaitingInputExecutor("Approve?"),
                        succeedingExecutor(WorkflowStep.StepType.SERVICE, Map.of("done", "yes"))),
                qds);
        try {
            runtime.declareWorkflow("resume-wf", List.of(
                    new WorkflowStep(WorkflowStep.StepType.TOOL, "pause-step"),
                    new WorkflowStep(WorkflowStep.StepType.SERVICE, "next-step")));

            WorkflowInstanceView started = runtime.startWorkflow("resume-wf", Map.of());
            awaitStatus(runtime, started.workflowId(), WorkflowStatus.WAITING_FOR_INPUT, Duration.ofSeconds(5));

            simulateAnswer(eventBus, started.workflowId(), "approved");

            WorkflowResultView result = awaitResult(runtime, started.workflowId(), Duration.ofSeconds(5));
            assertEquals(WorkflowStatus.SUCCEEDED, result.status());

            assertFalse(resumeEvents.isEmpty(), "WORKFLOW_RESUMED must be published");
            Event resumeEvent = resumeEvents.get(0);
            assertEquals(started.workflowId(), resumeEvent.metadata().get("workflowId"));
            assertNotNull(resumeEvent.metadata().get("questionId"));
        } finally {
            runtime.close();
        }
    }

    @Test
    void resumedWorkflow_injectsHumanInputIntoNextStepInputContext() throws Exception {
        SimpleEventBus eventBus = new SimpleEventBus();
        List<Map<String, Object>> capturedInputs = new CopyOnWriteArrayList<>();

        QuestionDeliveryService qds = stubDeliveryService(eventBus);

        WorkflowStepExecutor captureStep = new WorkflowStepExecutor() {
            @Override
            public WorkflowStep.StepType stepType() { return WorkflowStep.StepType.SERVICE; }

            @Override
            public WorkflowStepResult execute(WorkflowStep step, Map<String, Object> input) {
                capturedInputs.add(input);
                return WorkflowStepResult.success(Map.of());
            }
        };

        WorkflowRuntime runtime = new WorkflowRuntime(eventBus,
                List.of(awaitingInputExecutor("Enter amount"), captureStep), qds);
        try {
            runtime.declareWorkflow("inject-wf", List.of(
                    new WorkflowStep(WorkflowStep.StepType.TOOL, "pause-step"),
                    new WorkflowStep(WorkflowStep.StepType.SERVICE, "capture-step")));

            WorkflowInstanceView started = runtime.startWorkflow("inject-wf", Map.of());
            awaitStatus(runtime, started.workflowId(), WorkflowStatus.WAITING_FOR_INPUT, Duration.ofSeconds(5));

            simulateAnswer(eventBus, started.workflowId(), "approved");

            awaitResult(runtime, started.workflowId(), Duration.ofSeconds(5));

            assertFalse(capturedInputs.isEmpty(), "capture step must have been called");
            assertEquals("approved", capturedInputs.get(0).get("humanInput"),
                    "next step input must contain humanInput with the answer content");
        } finally {
            runtime.close();
        }
    }

    @Test
    void resumedWorkflow_withNoFurtherSteps_transitionsToSucceeded() throws Exception {
        SimpleEventBus eventBus = new SimpleEventBus();
        QuestionDeliveryService qds = stubDeliveryService(eventBus);
        WorkflowRuntime runtime = new WorkflowRuntime(eventBus,
                List.of(awaitingInputExecutor("Last step prompt")), qds);
        try {
            runtime.declareWorkflow("last-step-wf",
                    List.of(new WorkflowStep(WorkflowStep.StepType.TOOL, "pause-step")));

            WorkflowInstanceView started = runtime.startWorkflow("last-step-wf", Map.of());
            awaitStatus(runtime, started.workflowId(), WorkflowStatus.WAITING_FOR_INPUT, Duration.ofSeconds(5));

            simulateAnswer(eventBus, started.workflowId(), "done");

            WorkflowResultView result = awaitResult(runtime, started.workflowId(), Duration.ofSeconds(5));
            assertEquals(WorkflowStatus.SUCCEEDED, result.status());
        } finally {
            runtime.close();
        }
    }

    @Test
    void workflowPausedForInput_usesWorkflowIdAsSessionId() throws Exception {
        SimpleEventBus eventBus = new SimpleEventBus();
        List<Question> deliveredQuestions = new CopyOnWriteArrayList<>();
        QuestionDeliveryService qds = capturingDeliveryService(eventBus, deliveredQuestions);
        WorkflowRuntime runtime = new WorkflowRuntime(eventBus,
                List.of(awaitingInputExecutor("wf-123 prompt")), qds);
        try {
            runtime.declareWorkflow("corr-wf",
                    List.of(new WorkflowStep(WorkflowStep.StepType.TOOL, "pause-step")));

            WorkflowInstanceView started = runtime.startWorkflow("corr-wf", Map.of());
            awaitStatus(runtime, started.workflowId(), WorkflowStatus.WAITING_FOR_INPUT, Duration.ofSeconds(5));

            assertFalse(deliveredQuestions.isEmpty(), "question must have been delivered");
            Question delivered = deliveredQuestions.get(0);
            assertEquals(started.workflowId(), delivered.sessionId(),
                    "question sessionId must equal workflowId");
        } finally {
            runtime.close();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private WorkflowStepExecutor awaitingInputExecutor(String prompt) {
        return new WorkflowStepExecutor() {
            @Override
            public WorkflowStep.StepType stepType() { return WorkflowStep.StepType.TOOL; }

            @Override
            public WorkflowStepResult execute(WorkflowStep step, Map<String, Object> input) {
                return WorkflowStepResult.awaitingInput(prompt);
            }
        };
    }

    private WorkflowStepExecutor succeedingExecutor(WorkflowStep.StepType type,
                                                     Map<String, Object> output) {
        return new WorkflowStepExecutor() {
            @Override
            public WorkflowStep.StepType stepType() { return type; }

            @Override
            public WorkflowStepResult execute(WorkflowStep step, Map<String, Object> input) {
                return WorkflowStepResult.success(output);
            }
        };
    }

    private void simulateAnswer(EventBus eventBus, String workflowId, String content) {
        eventBus.publish(new Event(
                EventType.QUESTION_ANSWERED,
                System.currentTimeMillis(),
                workflowId,
                Map.of("sessionId", workflowId, "questionId", "sim-q-1", "content", content)));
    }

    private QuestionDeliveryService stubDeliveryService(EventBus eventBus) {
        QuestionRuntime questionRuntime = new QuestionRuntime(eventBus);
        return new QuestionDeliveryService(
                new LoggingDeliveryChannel(),
                new NoOpReplyCollector(),
                questionRuntime,
                new InMemoryQuestionStore());
    }

    private QuestionDeliveryService capturingDeliveryService(EventBus eventBus,
                                                              List<Question> captured) {
        QuestionRuntime questionRuntime = new QuestionRuntime(eventBus);
        QuestionDeliveryChannel capturingChannel = new QuestionDeliveryChannel() {
            @Override
            public void send(Question q) { captured.add(q); }

            @Override
            public void close() {}
        };
        return new QuestionDeliveryService(
                capturingChannel,
                new NoOpReplyCollector(),
                questionRuntime,
                new InMemoryQuestionStore());
    }

    private WorkflowResultView awaitResult(WorkflowRuntime runtime, String workflowId,
                                            Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        WorkflowResultView last = null;
        while (System.nanoTime() < deadline) {
            last = runtime.workflowResult(workflowId);
            if (last.status() == WorkflowStatus.SUCCEEDED
                    || last.status() == WorkflowStatus.FAILED
                    || last.status() == WorkflowStatus.CANCELLED) {
                return last;
            }
            sleepBriefly();
        }
        fail("Timed out waiting for workflow result; last status=" + (last == null ? "null" : last.status()));
        return null;
    }

    private WorkflowInstanceView awaitStatus(WorkflowRuntime runtime, String workflowId,
                                              WorkflowStatus expected, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        WorkflowInstanceView last = null;
        while (System.nanoTime() < deadline) {
            last = runtime.workflowState(workflowId);
            if (last.status() == expected) return last;
            sleepBriefly();
        }
        fail("Timed out waiting for workflow status " + expected
                + "; last status=" + (last == null ? "null" : last.status()));
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

    // -----------------------------------------------------------------------
    // Test infrastructure
    // -----------------------------------------------------------------------

    private static final class NoOpReplyCollector implements QuestionReplyCollector {
        @Override
        public void collect(String sessionId, String questionId, Answer answer) {}

        @Override
        public void close() {}
    }

    private static final class InMemoryQuestionStore implements QuestionStore {
        private final ConcurrentHashMap<String, Question> store = new ConcurrentHashMap<>();

        @Override
        public String save(Question question) {
            store.put(question.questionId(), question);
            return question.questionId();
        }

        @Override
        public Question update(String questionId, QuestionStatus status) {
            Question q = store.get(questionId);
            if (q == null) return null;
            Question updated = new Question(q.questionId(), q.sessionId(), q.question(),
                    q.impact(), q.recommendation(), status, q.category(), q.deliveryMode());
            store.put(questionId, updated);
            return updated;
        }

        @Override
        public List<Question> findBySession(String sessionId) {
            List<Question> result = new ArrayList<>();
            for (Question q : store.values()) {
                if (sessionId.equals(q.sessionId())) result.add(q);
            }
            return result;
        }

        @Override
        public List<Question> findByStatus(QuestionStatus status) {
            List<Question> result = new ArrayList<>();
            for (Question q : store.values()) {
                if (q.status() == status) result.add(q);
            }
            return result;
        }

        @Override
        public Optional<Question> findPending(String sessionId) {
            return findBySession(sessionId).stream()
                    .filter(q -> q.status() == QuestionStatus.WAITING_FOR_ANSWER)
                    .findFirst();
        }

        @Override
        public void delete(String questionId) {
            store.remove(questionId);
        }
    }
}
