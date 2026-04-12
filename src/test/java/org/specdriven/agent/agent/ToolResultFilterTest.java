package org.specdriven.agent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolResultFilterTest {

    private final ToolResultFilter filter = new DefaultToolResultFilter();

    @Test
    void relevantToolResultIsRetainedAndIrrelevantToolResultIsRemoved() {
        UserMessage userMessage = new UserMessage("check build status", 1L);
        ToolMessage relevantTool = new ToolMessage("build status is green", 2L, "bash", "call-1");
        ToolMessage irrelevantTool = new ToolMessage("archived deployment output", 3L, "grep", "call-2");
        AssistantMessage assistantMessage = new AssistantMessage("done", 4L);

        List<Message> filtered = filter.filter(ToolResultFilterInput.of(
                "summarize build status",
                List.of(),
                List.of(userMessage, relevantTool, irrelevantTool, assistantMessage)));

        assertEquals(List.of(userMessage, relevantTool, assistantMessage), filtered);
    }

    @Test
    void requestedToolNameCanRetainToolResultWithoutCurrentTurnText() {
        UserMessage userMessage = new UserMessage("continue", 1L);
        ToolMessage matchingTool = new ToolMessage("plain output", 2L, "grep", "call-1");
        ToolMessage unrelatedTool = new ToolMessage("plain output", 3L, "bash", "call-2");

        List<Message> filtered = filter.filter(ToolResultFilterInput.of(
                "",
                List.of("grep"),
                List.of(userMessage, matchingTool, unrelatedTool)));

        assertEquals(List.of(userMessage, matchingTool), filtered);
    }

    @Test
    void nonToolMessagesRemainInOriginalRelativeOrder() {
        SystemMessage systemMessage = new SystemMessage("system", 1L);
        UserMessage firstUserMessage = new UserMessage("compile code", 2L);
        ToolMessage irrelevantTool = new ToolMessage("runtime logs only", 3L, "bash", "call-1");
        AssistantMessage assistantMessage = new AssistantMessage("next", 4L);
        UserMessage secondUserMessage = new UserMessage("finish", 5L);

        List<Message> filtered = filter.filter(ToolResultFilterInput.of(
                "compile code",
                List.of(),
                List.of(systemMessage, firstUserMessage, irrelevantTool, assistantMessage, secondUserMessage)));

        assertEquals(List.of(systemMessage, firstUserMessage, assistantMessage, secondUserMessage), filtered);
    }

    @Test
    void mandatoryToolResultIsRetainedWhenRelevanceIsLow() {
        UserMessage userMessage = new UserMessage("compile artifacts", 1L);
        ToolMessage mandatoryTool = new ToolMessage("unrelated archived output", 2L, "grep", "call-1");
        ToolMessage irrelevantTool = new ToolMessage("unrelated archived output", 3L, "bash", "call-2");
        ToolResultFilterInput input = new ToolResultFilterInput(
                "compile artifacts",
                List.of(),
                List.of(userMessage, mandatoryTool, irrelevantTool),
                Map.of("call-1", ContextRetentionCandidate.recovery("resume execution")));

        List<Message> filtered = filter.filter(input);

        assertEquals(List.of(userMessage, mandatoryTool), filtered);
    }

    @Test
    void activeToolCallCorrelationIsRetainedWhenRelevanceIsLow() {
        UserMessage userMessage = new UserMessage("compile artifacts", 1L);
        ToolMessage activeToolCall = new ToolMessage("unrelated archived output", 2L, "grep", "call-1");
        ToolResultFilterInput input = new ToolResultFilterInput(
                "compile artifacts",
                List.of(),
                List.of(userMessage, activeToolCall),
                Map.of("call-1", ContextRetentionCandidate.activeToolCall("call-1")));

        List<Message> filtered = filter.filter(input);

        assertEquals(List.of(userMessage, activeToolCall), filtered);
    }

    @Test
    void missingOptionalRetentionMetadataDoesNotThrow() {
        ToolMessage relevantTool = new ToolMessage("build status is green", 1L, "bash", "call-1");
        ToolResultFilterInput input = new ToolResultFilterInput(
                "build status",
                null,
                List.of(new UserMessage("status", 0L), relevantTool),
                null);

        List<Message> filtered = filter.filter(input);

        assertTrue(filtered.contains(relevantTool));
    }

    @Test
    void identicalInputsProduceDeterministicImmutableResults() {
        List<Message> messages = List.of(
                new UserMessage("check build status", 1L),
                new ToolMessage("build status is green", 2L, "bash", "call-1"),
                new ToolMessage("runtime logs only", 3L, "grep", "call-2"));
        ToolResultFilterInput input = ToolResultFilterInput.of("build status", List.of(), messages);

        List<Message> first = filter.filter(input);
        List<Message> second = filter.filter(input);

        assertEquals(first, second);
        assertThrows(UnsupportedOperationException.class,
                () -> first.add(new UserMessage("extra", 4L)));
    }

    @Test
    void filterRequestPreservesNonMessageParameters() {
        UserMessage userMessage = new UserMessage("check build status", 1L);
        ToolMessage relevantTool = new ToolMessage("build status is green", 2L, "bash", "call-1");
        ToolMessage irrelevantTool = new ToolMessage("runtime logs only", 3L, "grep", "call-2");
        ToolSchema toolSchema = new ToolSchema("bash", "execute commands", Map.of("type", "object"));
        LlmRequest request = new LlmRequest(
                List.of(userMessage, relevantTool, irrelevantTool),
                "system prompt",
                List.of(toolSchema),
                0.3,
                128,
                Map.of("trace", "enabled"));

        LlmRequest filteredRequest = filter.filterRequest(
                request,
                ToolResultFilterInput.of("build status", List.of(), List.of()));

        assertEquals(List.of(userMessage, relevantTool), filteredRequest.messages());
        assertEquals(request.systemPrompt(), filteredRequest.systemPrompt());
        assertEquals(request.tools(), filteredRequest.tools());
        assertEquals(request.temperature(), filteredRequest.temperature());
        assertEquals(request.maxTokens(), filteredRequest.maxTokens());
        assertEquals(request.extra(), filteredRequest.extra());
    }
}
