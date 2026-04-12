package org.specdriven.agent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConversationSummarizerTest {

    private final ConversationSummarizer summarizer = new DefaultConversationSummarizer();

    @Test
    void messagesUnderTokenBudgetRemainUnchanged() {
        List<Message> messages = List.of(
                new UserMessage("short request", 1L),
                new AssistantMessage("short response", 2L));

        List<Message> summarized = summarizer.summarize(
                ConversationSummarizerInput.of(messages, 1, 10_000, 128));

        assertEquals(messages, summarized);
    }

    @Test
    void emptyInputReturnsImmutableEmptyOutput() {
        List<Message> summarized = summarizer.summarize(
                ConversationSummarizerInput.of(List.of(), 2, 100, 32));

        assertTrue(summarized.isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> summarized.add(new UserMessage("extra", 1L)));
    }

    @Test
    void nullMessagesAndRetentionMetadataAreHandledAsEmptyInputs() {
        List<Message> summarized = summarizer.summarize(
                new ConversationSummarizerInput(null, 2, 100, 32, null));

        assertTrue(summarized.isEmpty());
    }

    @Test
    void olderEligibleMessagesAreSummarizedWithRoleAndToolContext() {
        SystemMessage system = new SystemMessage("follow project rules", 1L);
        UserMessage oldUser = new UserMessage("investigate the failing build in module alpha", 2L);
        AssistantMessage oldAssistant = new AssistantMessage("I will inspect the compiler output", 3L);
        ToolMessage oldTool = new ToolMessage("compiler output shows missing import", 4L, "bash", "call-1");
        UserMessage recentUser = new UserMessage("now summarize the fix", 5L);
        AssistantMessage recentAssistant = new AssistantMessage("the fix is ready", 6L);

        List<Message> summarized = summarizer.summarize(ConversationSummarizerInput.of(
                List.of(system, oldUser, oldAssistant, oldTool, recentUser, recentAssistant),
                2,
                1,
                96));

        assertEquals(4, summarized.size());
        assertEquals(system, summarized.get(0));
        AssistantMessage summary = assertInstanceOf(AssistantMessage.class, summarized.get(1));
        assertTrue(summary.content().contains("compressed 3 earlier messages"));
        assertTrue(summary.content().contains("user=1"));
        assertTrue(summary.content().contains("assistant=1"));
        assertTrue(summary.content().contains("tool=1"));
        assertTrue(summary.content().contains("bash=1"));
        assertEquals(recentUser, summarized.get(2));
        assertEquals(recentAssistant, summarized.get(3));
        assertTrue(TokenCounter.estimate(summary.content()) <= 96);
    }

    @Test
    void recentMessagesRemainCompleteAndOrdered() {
        UserMessage oldUser = new UserMessage("old context " + "a ".repeat(100), 1L);
        AssistantMessage middleAssistant = new AssistantMessage("middle context " + "b ".repeat(100), 2L);
        ToolMessage recentTool = new ToolMessage("recent tool output", 3L, "grep", "call-1");
        UserMessage recentUser = new UserMessage("recent user request", 4L);
        AssistantMessage recentAssistant = new AssistantMessage("recent answer", 5L);

        List<Message> summarized = summarizer.summarize(ConversationSummarizerInput.of(
                List.of(oldUser, middleAssistant, recentTool, recentUser, recentAssistant),
                3,
                1,
                96));

        assertEquals(List.of(recentTool, recentUser, recentAssistant),
                summarized.subList(summarized.size() - 3, summarized.size()));
    }

    @Test
    void systemMessagesRemainCompleteBeforeGeneratedSummary() {
        SystemMessage firstSystem = new SystemMessage("system one", 1L);
        UserMessage oldUser = new UserMessage("old context " + "a ".repeat(100), 2L);
        SystemMessage secondSystem = new SystemMessage("system two", 3L);
        AssistantMessage oldAssistant = new AssistantMessage("old response " + "b ".repeat(100), 4L);
        UserMessage recentUser = new UserMessage("recent request", 5L);

        List<Message> summarized = summarizer.summarize(ConversationSummarizerInput.of(
                List.of(firstSystem, oldUser, secondSystem, oldAssistant, recentUser),
                1,
                1,
                96));

        assertEquals(firstSystem, summarized.get(0));
        assertEquals(secondSystem, summarized.get(1));
        AssistantMessage summary = assertInstanceOf(AssistantMessage.class, summarized.get(2));
        assertTrue(summary.content().startsWith("Conversation summary:"));
        assertEquals(recentUser, summarized.get(3));
    }

    @Test
    void mandatoryOlderMessagesRemainCompleteWhenOtherOlderMessagesAreSummarized() {
        UserMessage oldUser = new UserMessage("old ordinary context " + "a ".repeat(100), 1L);
        AssistantMessage mandatoryAssistant = new AssistantMessage("accepted human answer must replay", 2L);
        ToolMessage activeTool = new ToolMessage("active result must stay complete", 3L, "bash", "call-1");
        AssistantMessage oldAssistant = new AssistantMessage("old ordinary answer " + "b ".repeat(100), 4L);
        UserMessage recentUser = new UserMessage("recent request", 5L);
        Map<String, ContextRetentionCandidate> retention = Map.of(
                ConversationSummarizerInput.messageKey(mandatoryAssistant),
                ContextRetentionCandidate.answerReplay("question-1"),
                "call-1",
                ContextRetentionCandidate.activeToolCall("call-1"));

        List<Message> summarized = summarizer.summarize(new ConversationSummarizerInput(
                List.of(oldUser, mandatoryAssistant, activeTool, oldAssistant, recentUser),
                1,
                1,
                96,
                retention));

        assertTrue(summarized.contains(mandatoryAssistant));
        assertTrue(summarized.contains(activeTool));
        assertTrue(summarized.contains(recentUser));
        assertTrue(summarized.stream().anyMatch(message ->
                message instanceof AssistantMessage assistant
                        && assistant.content().contains("compressed 2 earlier messages")));
    }

    @Test
    void identicalInputsProduceDeterministicImmutableResults() {
        List<Message> messages = List.of(
                new UserMessage("old context " + "a ".repeat(100), 1L),
                new AssistantMessage("old answer " + "b ".repeat(100), 2L),
                new UserMessage("recent request", 3L));
        ConversationSummarizerInput input = ConversationSummarizerInput.of(messages, 1, 1, 96);

        List<Message> first = summarizer.summarize(input);
        List<Message> second = summarizer.summarize(input);

        assertEquals(first, second);
        assertThrows(UnsupportedOperationException.class,
                () -> first.add(new UserMessage("extra", 4L)));
    }

    @Test
    void summarizeRequestPreservesNonMessageParameters() {
        UserMessage oldUser = new UserMessage("old context " + "a ".repeat(100), 1L);
        AssistantMessage oldAssistant = new AssistantMessage("old answer " + "b ".repeat(100), 2L);
        UserMessage recentUser = new UserMessage("recent request", 3L);
        ToolSchema toolSchema = new ToolSchema("bash", "execute commands", Map.of("type", "object"));
        LlmRequest request = new LlmRequest(
                List.of(oldUser, oldAssistant, recentUser),
                "system prompt",
                List.of(toolSchema),
                0.3,
                128,
                Map.of("trace", "enabled"));

        LlmRequest summarizedRequest = summarizer.summarizeRequest(
                request,
                ConversationSummarizerInput.of(List.of(), 1, 1, 96));

        assertEquals(2, summarizedRequest.messages().size());
        assertTrue(summarizedRequest.messages().get(0).content().contains("compressed 2 earlier messages"));
        assertEquals(recentUser, summarizedRequest.messages().get(1));
        assertEquals(request.systemPrompt(), summarizedRequest.systemPrompt());
        assertEquals(request.tools(), summarizedRequest.tools());
        assertEquals(request.temperature(), summarizedRequest.temperature());
        assertEquals(request.maxTokens(), summarizedRequest.maxTokens());
        assertEquals(request.extra(), summarizedRequest.extra());
    }
}
