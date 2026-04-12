package org.specdriven.agent.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * LlmClient decorator that optimizes context before forwarding requests.
 */
public final class SmartContextInjector implements LlmClient {

    private final LlmClient delegate;
    private final SmartContextInjectorConfig config;
    private final ToolResultFilter toolResultFilter;
    private final ConversationSummarizer conversationSummarizer;

    public SmartContextInjector(LlmClient delegate, SmartContextInjectorConfig config) {
        this(delegate, config, new DefaultToolResultFilter(), new DefaultConversationSummarizer());
    }

    public SmartContextInjector(LlmClient delegate,
                                SmartContextInjectorConfig config,
                                ToolResultFilter toolResultFilter,
                                ConversationSummarizer conversationSummarizer) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.config = Objects.requireNonNull(config, "config");
        this.toolResultFilter = Objects.requireNonNull(toolResultFilter, "toolResultFilter");
        this.conversationSummarizer = Objects.requireNonNull(conversationSummarizer, "conversationSummarizer");
    }

    @Override
    public LlmResponse chat(List<Message> messages) {
        Objects.requireNonNull(messages, "messages");
        if (!config.enabled()) {
            return delegate.chat(messages);
        }
        return delegate.chat(optimize(messages));
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        Objects.requireNonNull(request, "request");
        if (!config.enabled()) {
            return delegate.chat(request);
        }
        List<Message> optimizedMessages = optimize(request.messages());
        return delegate.chat(new LlmRequest(
                optimizedMessages,
                request.systemPrompt(),
                request.tools(),
                request.temperature(),
                request.maxTokens(),
                request.extra()));
    }

    @Override
    public void chatStreaming(LlmRequest request, LlmStreamCallback callback) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(callback, "callback");
        if (!config.enabled()) {
            delegate.chatStreaming(request, callback);
            return;
        }
        delegate.chatStreaming(new LlmRequest(
                optimize(request.messages()),
                request.systemPrompt(),
                request.tools(),
                request.temperature(),
                request.maxTokens(),
                request.extra()), callback);
    }

    public List<Message> optimize(List<Message> messages) {
        Objects.requireNonNull(messages, "messages");
        if (!config.enabled()) {
            return messages;
        }
        String currentTurnText = currentTurnText(messages);
        List<String> requestedToolNames = config.useExplicitCurrentTurn()
                ? config.requestedToolNames()
                : List.of();

        List<Message> filtered = toolResultFilter.filter(new ToolResultFilterInput(
                currentTurnText,
                requestedToolNames,
                messages,
                config.retentionCandidatesByToolCallId()));

        return conversationSummarizer.summarize(new ConversationSummarizerInput(
                filtered,
                config.recentMessageLimit(),
                config.tokenBudget(),
                config.summaryTokenBudget(),
                mergedSummarizerRetentionCandidates()));
    }

    public EvaluationResult evaluate(List<Message> baselineMessages, List<String> criticalMarkers) {
        Objects.requireNonNull(baselineMessages, "baselineMessages");
        List<Message> optimizedMessages = optimize(baselineMessages);
        int baselineTokens = TokenCounter.estimate(baselineMessages);
        int optimizedTokens = TokenCounter.estimate(optimizedMessages);
        double reductionPercent = baselineTokens == 0
                ? 0.0
                : ((double) (baselineTokens - optimizedTokens) * 100.0) / baselineTokens;
        boolean criticalContextPreserved = criticalMarkers == null || criticalMarkers.stream()
                .filter(marker -> marker != null && !marker.isBlank())
                .allMatch(marker -> containsMarker(optimizedMessages, marker));
        return new EvaluationResult(
                baselineTokens,
                optimizedTokens,
                reductionPercent,
                criticalContextPreserved);
    }

    private String currentTurnText(List<Message> messages) {
        if (config.useExplicitCurrentTurn()) {
            return config.currentTurnText();
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message instanceof UserMessage) {
                return message.content() == null ? "" : message.content();
            }
        }
        return "";
    }

    private static boolean containsMarker(List<Message> messages, String marker) {
        return messages.stream().anyMatch(message -> message.content() != null
                && message.content().contains(marker));
    }

    private Map<String, ContextRetentionCandidate> mergedSummarizerRetentionCandidates() {
        if (config.retentionCandidatesByToolCallId().isEmpty()
                && config.retentionCandidatesByMessageKey().isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, ContextRetentionCandidate> merged = new LinkedHashMap<>();
        merged.putAll(config.retentionCandidatesByToolCallId());
        merged.putAll(config.retentionCandidatesByMessageKey());
        return Map.copyOf(merged);
    }

    public record EvaluationResult(
            int baselineEstimatedTokens,
            int optimizedEstimatedTokens,
            double tokenReductionPercent,
            boolean criticalContextPreserved
    ) {
    }
}
