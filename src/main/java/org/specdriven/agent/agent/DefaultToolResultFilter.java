package org.specdriven.agent.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Default ToolMessage filter backed by relevance scoring and retention policy decisions.
 */
public final class DefaultToolResultFilter implements ToolResultFilter {

    private static final int RELEVANT_SCORE_THRESHOLD = 1;

    private final ContextRelevanceScorer relevanceScorer;
    private final ContextRetentionPolicy retentionPolicy;

    public DefaultToolResultFilter() {
        this(new KeywordContextRelevanceScorer(), new DefaultContextRetentionPolicy());
    }

    public DefaultToolResultFilter(ContextRelevanceScorer relevanceScorer, ContextRetentionPolicy retentionPolicy) {
        this.relevanceScorer = Objects.requireNonNull(relevanceScorer, "relevanceScorer");
        this.retentionPolicy = Objects.requireNonNull(retentionPolicy, "retentionPolicy");
    }

    @Override
    public List<Message> filter(ToolResultFilterInput input) {
        Objects.requireNonNull(input, "input");

        ContextRelevanceScorer.CurrentTurn currentTurn = new ContextRelevanceScorer.CurrentTurn(
                input.currentTurnText(),
                input.requestedToolNames());
        List<Message> filtered = new ArrayList<>(input.messages().size());
        for (Message message : input.messages()) {
            if (!(message instanceof ToolMessage toolMessage)) {
                filtered.add(message);
                continue;
            }
            if (shouldRetainToolMessage(input, currentTurn, toolMessage)) {
                filtered.add(toolMessage);
            }
        }
        return List.copyOf(filtered);
    }

    private boolean shouldRetainToolMessage(ToolResultFilterInput input,
            ContextRelevanceScorer.CurrentTurn currentTurn, ToolMessage toolMessage) {
        ContextRelevanceScorer.PriorToolResult priorToolResult = new ContextRelevanceScorer.PriorToolResult(
                toolMessage.toolName(),
                toolMessage.content());
        int relevanceScore = relevanceScorer.score(currentTurn, priorToolResult);
        boolean relevant = relevanceScore >= RELEVANT_SCORE_THRESHOLD;
        ContextRetentionCandidate retentionCandidate = input.retentionCandidateFor(toolMessage, relevant);
        ContextRetentionDecision retentionDecision = retentionPolicy.evaluate(retentionCandidate);
        return retentionDecision.mandatory() || relevant;
    }
}
