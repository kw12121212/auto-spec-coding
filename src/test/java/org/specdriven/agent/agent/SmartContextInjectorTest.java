package org.specdriven.agent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SmartContextInjectorTest {

    @Test
    void listBasedCallsDelegateOnceWithOptimizedMessages() {
        LlmResponse.TextResponse response = new LlmResponse.TextResponse("ok");
        RecordingLlmClient delegate = new RecordingLlmClient(response);
        SmartContextInjector injector = new SmartContextInjector(
                delegate,
                SmartContextInjectorConfig.defaults(10_000));
        UserMessage user = new UserMessage("summarize build status", 1L);
        ToolMessage relevant = new ToolMessage("build status is green", 2L, "bash", "call-1");
        ToolMessage irrelevant = new ToolMessage("archived deployment output", 3L, "grep", "call-2");

        LlmResponse actual = injector.chat(List.of(user, relevant, irrelevant));

        assertSame(response, actual);
        assertEquals(1, delegate.listCalls.size());
        assertEquals(List.of(user, relevant), delegate.listCalls.get(0));
    }

    @Test
    void requestBasedCallsPreserveRequestParametersAndDelegateResponse() {
        LlmResponse.TextResponse response = new LlmResponse.TextResponse("ok");
        RecordingLlmClient delegate = new RecordingLlmClient(response);
        SmartContextInjector injector = new SmartContextInjector(
                delegate,
                SmartContextInjectorConfig.defaults(10_000));
        UserMessage user = new UserMessage("check build status", 1L);
        ToolMessage relevant = new ToolMessage("build status is green", 2L, "bash", "call-1");
        ToolMessage irrelevant = new ToolMessage("runtime log", 3L, "grep", "call-2");
        ToolSchema schema = new ToolSchema("bash", "execute commands", Map.of("type", "object"));
        LlmRequest request = new LlmRequest(
                List.of(user, relevant, irrelevant),
                "system",
                List.of(schema),
                0.2,
                128,
                Map.of("trace", "on"));

        LlmResponse actual = injector.chat(request);

        assertSame(response, actual);
        assertEquals(1, delegate.requestCalls.size());
        LlmRequest forwarded = delegate.requestCalls.get(0);
        assertEquals(List.of(user, relevant), forwarded.messages());
        assertEquals(request.systemPrompt(), forwarded.systemPrompt());
        assertEquals(request.tools(), forwarded.tools());
        assertEquals(request.temperature(), forwarded.temperature());
        assertEquals(request.maxTokens(), forwarded.maxTokens());
        assertEquals(request.extra(), forwarded.extra());
    }

    @Test
    void toolResultsAreFilteredBeforeSummarization() {
        RecordingLlmClient delegate = new RecordingLlmClient(new LlmResponse.TextResponse("ok"));
        SmartContextInjector injector = new SmartContextInjector(
                delegate,
                new SmartContextInjectorConfig(true, 1, 1, 96, false, "", List.of(), null, null));
        UserMessage oldUser = new UserMessage("old planning context " + "alpha ".repeat(80), 1L);
        ToolMessage irrelevantTool = new ToolMessage("unrelated archived output", 2L, "grep", "call-1");
        AssistantMessage oldAssistant = new AssistantMessage("old answer " + "beta ".repeat(80), 3L);
        UserMessage recentUser = new UserMessage("recent build request", 4L);

        injector.chat(List.of(oldUser, irrelevantTool, oldAssistant, recentUser));

        List<Message> forwarded = delegate.listCalls.get(0);
        assertFalse(forwarded.contains(irrelevantTool));
        AssistantMessage summary = (AssistantMessage) forwarded.get(0);
        assertTrue(summary.content().contains("compressed 2 earlier messages"));
        assertFalse(summary.content().contains("tool=1"));
        assertEquals(recentUser, forwarded.get(1));
    }

    @Test
    void mandatoryContextSurvivesFilteringAndSummarization() {
        AssistantMessage recovery = new AssistantMessage("RECOVERY_MARKER resume from phase", 1L);
        AssistantMessage answer = new AssistantMessage("ANSWER_MARKER accepted answer", 2L);
        ToolMessage activeTool = new ToolMessage("ACTIVE_TOOL_MARKER pending output", 3L, "grep", "call-1");
        UserMessage oldUser = new UserMessage("ordinary old context " + "gamma ".repeat(80), 4L);
        UserMessage recentUser = new UserMessage("recent unrelated request", 5L);
        SmartContextInjectorConfig config = new SmartContextInjectorConfig(
                true,
                1,
                1,
                96,
                false,
                "",
                List.of(),
                Map.of("call-1", ContextRetentionCandidate.activeToolCall("call-1")),
                Map.of(
                        ConversationSummarizerInput.messageKey(recovery),
                        ContextRetentionCandidate.recovery("recovery"),
                        ConversationSummarizerInput.messageKey(answer),
                        ContextRetentionCandidate.answerReplay("question-1")));
        RecordingLlmClient delegate = new RecordingLlmClient(new LlmResponse.TextResponse("ok"));
        SmartContextInjector injector = new SmartContextInjector(delegate, config);

        injector.chat(List.of(recovery, answer, activeTool, oldUser, recentUser));

        List<Message> forwarded = delegate.listCalls.get(0);
        assertTrue(forwarded.contains(recovery));
        assertTrue(forwarded.contains(answer));
        assertTrue(forwarded.contains(activeTool));
        assertTrue(forwarded.contains(recentUser));
    }

    @Test
    void explicitCurrentTurnMetadataIsUsedForToolRelevance() {
        ToolMessage grepOutput = new ToolMessage("plain output", 1L, "grep", "call-1");
        UserMessage latestUser = new UserMessage("latest unrelated request", 2L);
        RecordingLlmClient delegate = new RecordingLlmClient(new LlmResponse.TextResponse("ok"));
        SmartContextInjector injector = new SmartContextInjector(
                delegate,
                SmartContextInjectorConfig.defaults(10_000).withCurrentTurn("", List.of("grep")));

        injector.chat(List.of(grepOutput, latestUser));

        assertEquals(List.of(grepOutput, latestUser), delegate.listCalls.get(0));
    }

    @Test
    void missingCurrentTurnMetadataWithoutUserMessageDoesNotThrow() {
        SystemMessage system = new SystemMessage("system", 1L);
        ToolMessage tool = new ToolMessage("orphaned output", 2L, "bash", "call-1");
        RecordingLlmClient delegate = new RecordingLlmClient(new LlmResponse.TextResponse("ok"));
        SmartContextInjector injector = new SmartContextInjector(
                delegate,
                SmartContextInjectorConfig.defaults(10_000));

        injector.chat(List.of(system, tool));

        assertEquals(List.of(system), delegate.listCalls.get(0));
    }

    @Test
    void disabledInjectorIsTransparent() {
        List<Message> messages = List.of(
                new UserMessage("request", 1L),
                new ToolMessage("tool output", 2L, "grep", "call-1"));
        RecordingLlmClient delegate = new RecordingLlmClient(new LlmResponse.TextResponse("ok"));
        SmartContextInjector injector = new SmartContextInjector(
                delegate,
                SmartContextInjectorConfig.disabled());

        injector.chat(messages);

        assertSame(messages, delegate.listCalls.get(0));
    }

    @Test
    void invalidBudgetsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SmartContextInjectorConfig.defaults(0));
        assertThrows(IllegalArgumentException.class,
                () -> new SmartContextInjectorConfig(true, -1, 10, 2, false, "", List.of(), null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new SmartContextInjectorConfig(true, 1, 10, 0, false, "", List.of(), null, null));
    }

    @Test
    void fixedEvaluationSetReportsReductionAndPreservesCriticalMarkers() {
        AssistantMessage recovery = new AssistantMessage("RECOVERY_MARKER resume checkpoint", 1L);
        AssistantMessage question = new AssistantMessage("QUESTION_MARKER waiting human decision", 2L);
        AssistantMessage answer = new AssistantMessage("ANSWER_MARKER accepted answer replay", 3L);
        AssistantMessage audit = new AssistantMessage("AUDIT_MARKER trace entry", 4L);
        ToolMessage activeTool = new ToolMessage("ACTIVE_TOOL_MARKER pending result", 5L, "bash", "call-1");
        ToolMessage irrelevantTool = new ToolMessage("irrelevant log " + "noise ".repeat(160), 6L, "grep", "call-2");
        UserMessage oldUser = new UserMessage("old request " + "context ".repeat(160), 7L);
        AssistantMessage oldAssistant = new AssistantMessage("old answer " + "details ".repeat(160), 8L);
        UserMessage recentUser = new UserMessage("recent request", 9L);
        SmartContextInjectorConfig config = new SmartContextInjectorConfig(
                true,
                1,
                1,
                96,
                false,
                "",
                List.of(),
                Map.of("call-1", ContextRetentionCandidate.activeToolCall("call-1")),
                Map.of(
                        ConversationSummarizerInput.messageKey(recovery),
                        ContextRetentionCandidate.recovery("recovery"),
                        ConversationSummarizerInput.messageKey(question),
                        ContextRetentionCandidate.questionEscalation("question-1"),
                        ConversationSummarizerInput.messageKey(answer),
                        ContextRetentionCandidate.answerReplay("question-1"),
                        ConversationSummarizerInput.messageKey(audit),
                        ContextRetentionCandidate.auditTrace("session-1")));
        SmartContextInjector injector = new SmartContextInjector(
                new RecordingLlmClient(new LlmResponse.TextResponse("ok")),
                config);

        SmartContextInjector.EvaluationResult result = injector.evaluate(
                List.of(recovery, question, answer, audit, activeTool, irrelevantTool, oldUser,
                        oldAssistant, recentUser),
                List.of("RECOVERY_MARKER", "QUESTION_MARKER", "ANSWER_MARKER", "AUDIT_MARKER",
                        "ACTIVE_TOOL_MARKER"));

        assertTrue(result.tokenReductionPercent() >= 30.0);
        assertTrue(result.criticalContextPreserved());
        assertTrue(result.baselineEstimatedTokens() > result.optimizedEstimatedTokens());
    }

    private static final class RecordingLlmClient implements LlmClient {
        private final LlmResponse response;
        private final List<List<Message>> listCalls = new ArrayList<>();
        private final List<LlmRequest> requestCalls = new ArrayList<>();

        private RecordingLlmClient(LlmResponse response) {
            this.response = response;
        }

        @Override
        public LlmResponse chat(List<Message> messages) {
            listCalls.add(messages);
            return response;
        }

        @Override
        public LlmResponse chat(LlmRequest request) {
            requestCalls.add(request);
            return response;
        }
    }
}
