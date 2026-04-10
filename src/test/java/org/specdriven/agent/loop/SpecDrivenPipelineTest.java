package org.specdriven.agent.loop;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.agent.AgentState;
import org.specdriven.agent.agent.AssistantMessage;
import org.specdriven.agent.agent.Conversation;
import org.specdriven.agent.agent.LlmClient;
import org.specdriven.agent.agent.LlmResponse;
import org.specdriven.agent.agent.Message;
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
