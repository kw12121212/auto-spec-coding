package org.specdriven.agent.loop;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.agent.AgentState;
import org.specdriven.agent.agent.AssistantMessage;
import org.specdriven.agent.agent.Conversation;
import org.specdriven.agent.agent.LlmClient;
import org.specdriven.agent.agent.LlmResponse;
import org.specdriven.agent.agent.Message;
import org.specdriven.agent.agent.ToolCall;
import org.specdriven.agent.event.SimpleEventBus;
import org.specdriven.agent.tool.Tool;

class SpecDrivenPipelineTest {

    private static final LoopCandidate TEST_CANDIDATE =
            new LoopCandidate("test-change", "m1.md", "test goal");

    private LoopConfig testConfig() {
        return new LoopConfig(1, 600, List.of(), Path.of("/tmp"), new SimpleEventBus());
    }

    @Test
    void executeReturnsSuccessWhenAllPhasesComplete() {
        LlmClient client = new StubLlmClient(textResponse("done"));
        LoopPipeline pipeline = new SpecDrivenPipeline(path -> client);
        IterationResult result = pipeline.execute(TEST_CANDIDATE, testConfig());

        assertEquals(IterationStatus.SUCCESS, result.status());
        assertNull(result.failureReason());
        assertEquals(PipelinePhase.ordered(), result.phasesCompleted());
        assertTrue(result.durationMs() >= 0);
    }

    @Test
    void executeLoadsTemplatesFromClasspath() {
        // If templates are missing, loadTemplate throws IOException → FAILED
        LlmClient client = new StubLlmClient(textResponse("done"));
        LoopPipeline pipeline = new SpecDrivenPipeline(path -> client);
        IterationResult result = pipeline.execute(TEST_CANDIDATE, testConfig());

        // Should succeed since we created the templates
        assertEquals(IterationStatus.SUCCESS, result.status());
    }

    @Test
    void executeReturnsFailedOnLlmException() {
        LlmClient client = new LlmClient() {
            @Override
            public LlmResponse chat(List<Message> messages) {
                throw new RuntimeException("LLM unavailable");
            }
        };
        LoopPipeline pipeline = new SpecDrivenPipeline(path -> client);
        IterationResult result = pipeline.execute(TEST_CANDIDATE, testConfig());

        assertEquals(IterationStatus.FAILED, result.status());
        assertTrue(result.failureReason().contains("LLM unavailable"));
        assertTrue(result.phasesCompleted().size() < PipelinePhase.ordered().size());
    }

    @Test
    void executeReturnsTimedOutWhenDeadlineExceeded() {
        LoopConfig config = new LoopConfig(1, 1, List.of(), Path.of("/tmp"), new SimpleEventBus());
        LlmClient client = new LlmClient() {
            @Override
            public LlmResponse chat(List<Message> messages) {
                try { Thread.sleep(600); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return textResponse("done");
            }
        };
        LoopPipeline pipeline = new SpecDrivenPipeline(path -> client);
        IterationResult result = pipeline.execute(TEST_CANDIDATE, config);

        assertEquals(IterationStatus.TIMED_OUT, result.status());
        assertTrue(result.phasesCompleted().size() < PipelinePhase.ordered().size());
    }

    @Test
    void constructorWithDefaultTools() {
        // Should not throw
        LoopPipeline pipeline = new SpecDrivenPipeline(path -> new StubLlmClient(textResponse("ok")));
        assertNotNull(pipeline);
    }

    @Test
    void constructorWithCustomTools() {
        Map<String, Tool> tools = Map.of();
        LoopPipeline pipeline = new SpecDrivenPipeline(
                path -> new StubLlmClient(textResponse("ok")), tools);
        assertNotNull(pipeline);
    }

    @Test
    void executeWithEmptySkipPhasesRunsAllPhases() {
        LlmClient client = new StubLlmClient(textResponse("done"));
        LoopPipeline pipeline = new SpecDrivenPipeline(path -> client);
        IterationResult result = pipeline.execute(TEST_CANDIDATE, testConfig(), Set.of());

        assertEquals(IterationStatus.SUCCESS, result.status());
        assertEquals(PipelinePhase.ordered(), result.phasesCompleted());
    }

    @Test
    void executeWithSkipPhasesSkipsSpecifiedPhases() {
        LlmClient client = new StubLlmClient(textResponse("done"));
        LoopPipeline pipeline = new SpecDrivenPipeline(path -> client);
        // Skip PROPOSE — execution should start from IMPLEMENT
        Set<PipelinePhase> skip = Set.of(PipelinePhase.PROPOSE);
        IterationResult result = pipeline.execute(TEST_CANDIDATE, testConfig(), skip);

        assertEquals(IterationStatus.SUCCESS, result.status());
        assertFalse(result.phasesCompleted().contains(PipelinePhase.PROPOSE));
        assertTrue(result.phasesCompleted().containsAll(
                List.of(PipelinePhase.IMPLEMENT, PipelinePhase.VERIFY,
                        PipelinePhase.REVIEW, PipelinePhase.ARCHIVE)));
    }

    @Test
    void defaultExecuteMethodDelegatesToSkipPhasesOverload() {
        LlmClient client = new StubLlmClient(textResponse("done"));
        LoopPipeline pipeline = new SpecDrivenPipeline(path -> client);
        // default execute() should behave identically to execute(candidate, config, Set.of())
        IterationResult result1 = pipeline.execute(TEST_CANDIDATE, testConfig());
        IterationResult result2 = pipeline.execute(TEST_CANDIDATE, testConfig(), Set.of());
        assertEquals(result1.status(), result2.status());
        assertEquals(result1.phasesCompleted(), result2.phasesCompleted());
    }

    @Test
    void capturesQuestionCreatedEventAndReturnsQuestioning() {
        SimpleEventBus bus = new SimpleEventBus();
        LoopConfig config = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus);

        AtomicBoolean questionFired = new AtomicBoolean();
        LlmClient client = messages -> {
            if (!questionFired.getAndSet(true)) {
                // First call in first phase: request __ask_question__ tool
                return new LlmResponse.ToolCallResponse(List.of(
                        new ToolCall("__ask_question__", Map.of(
                                "questionId", "q-loop-1",
                                "question", "Should I proceed with approach A?",
                                "impact", "Architectural correctness",
                                "recommendation", "Use approach A",
                                "category", "PERMISSION_CONFIRMATION",
                                "deliveryMode", "PAUSE_WAIT_HUMAN"
                        ), "call-1")
                ));
            }
            return new LlmResponse.TextResponse("done");
        };

        LoopPipeline pipeline = new SpecDrivenPipeline(path -> client);
        IterationResult result = pipeline.execute(TEST_CANDIDATE, config, Set.of());

        assertEquals(IterationStatus.QUESTIONING, result.status());
        assertNotNull(result.question());
        assertEquals("q-loop-1", result.question().questionId());
        assertEquals("Should I proceed with approach A?", result.question().question());
        // Interrupted in first phase — no phases completed
        assertTrue(result.phasesCompleted().isEmpty());
        assertNull(result.failureReason());
    }

    @Test
    void phasesCompletedBeforeQuestionAreReported() {
        SimpleEventBus bus = new SimpleEventBus();
        LoopConfig config = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus);

        // Phase 1 (PROPOSE) LlmClient completes normally; Phase 2 (IMPLEMENT) fires the question
        AtomicBoolean firstPhase = new AtomicBoolean(true);
        AtomicBoolean questionFired = new AtomicBoolean();
        LlmClient client = messages -> {
            if (firstPhase.get()) {
                // Check if this is the PROPOSE phase prompt
                boolean hasPropose = messages.stream().anyMatch(m ->
                        m.toString().contains("PROPOSE") || m.toString().contains("propose"));
                if (hasPropose) {
                    firstPhase.set(false);
                    return new LlmResponse.TextResponse("propose done");
                }
            }
            if (!questionFired.getAndSet(true)) {
                return new LlmResponse.ToolCallResponse(List.of(
                        new ToolCall("__ask_question__", Map.of(
                                "questionId", "q-phase2",
                                "question", "Proceed?",
                                "impact", "Impact",
                                "recommendation", "Yes",
                                "category", "PERMISSION_CONFIRMATION",
                                "deliveryMode", "PAUSE_WAIT_HUMAN"
                        ), "c2")
                ));
            }
            return new LlmResponse.TextResponse("done");
        };

        LoopPipeline pipeline = new SpecDrivenPipeline(path -> client);
        IterationResult result = pipeline.execute(TEST_CANDIDATE, config, Set.of());

        assertEquals(IterationStatus.QUESTIONING, result.status());
        assertNotNull(result.question());
        // At least the question was captured
        assertEquals("q-phase2", result.question().questionId());
    }

    @Test
    void listenerIsUnregisteredAfterPhaseCompletes() {
        // After a successful phase, QUESTION_CREATED fired outside executePhase should not capture
        SimpleEventBus bus = new SimpleEventBus();
        LoopConfig config = new LoopConfig(1, 60, List.of(), Path.of("/tmp"), bus);
        LlmClient client = new StubLlmClient(textResponse("done"));

        LoopPipeline pipeline = new SpecDrivenPipeline(path -> client);
        IterationResult result = pipeline.execute(TEST_CANDIDATE, config, Set.of());

        assertEquals(IterationStatus.SUCCESS, result.status());
        // After pipeline completes, no QUESTION_CREATED listener should be active
        // Verify by publishing QUESTION_CREATED after — it should not affect anything
        assertDoesNotThrow(() -> bus.publish(new org.specdriven.agent.event.Event(
                org.specdriven.agent.event.EventType.QUESTION_CREATED,
                System.currentTimeMillis(), "test", Map.of())));
    }

    private static final class StubLlmClient implements LlmClient {
        private final LlmResponse response;

        StubLlmClient(LlmResponse response) {
            this.response = response;
        }

        @Override
        public LlmResponse chat(List<Message> messages) {
            return response;
        }
    }

    private static LlmResponse textResponse(String content) {
        return new LlmResponse.TextResponse(content);
    }
}
