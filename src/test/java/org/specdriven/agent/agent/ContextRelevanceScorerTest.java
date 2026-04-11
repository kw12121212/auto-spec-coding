package org.specdriven.agent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContextRelevanceScorerTest {

    private final ContextRelevanceScorer scorer = new KeywordContextRelevanceScorer();

    @Test
    void matchingToolNameIncreasesRelevance() {
        ContextRelevanceScorer.CurrentTurn currentTurn = new ContextRelevanceScorer.CurrentTurn(
                "show recent output",
                List.of("grep"));

        int matchingScore = scorer.score(currentTurn,
                new ContextRelevanceScorer.PriorToolResult("grep", "unrelated content"));
        int nonMatchingScore = scorer.score(currentTurn,
                new ContextRelevanceScorer.PriorToolResult("bash", "unrelated content"));

        assertTrue(matchingScore > nonMatchingScore);
    }

    @Test
    void matchingTextKeywordsIncreaseRelevance() {
        ContextRelevanceScorer.CurrentTurn currentTurn = new ContextRelevanceScorer.CurrentTurn(
                "find build status",
                List.of());

        int overlappingScore = scorer.score(currentTurn,
                new ContextRelevanceScorer.PriorToolResult("bash", "latest build status is green"));
        int nonOverlappingScore = scorer.score(currentTurn,
                new ContextRelevanceScorer.PriorToolResult("bash", "runtime logs were archived"));

        assertTrue(overlappingScore > nonOverlappingScore);
    }

    @Test
    void noOverlapUsesLowestRelevanceBand() {
        ContextRelevanceScorer.CurrentTurn currentTurn = new ContextRelevanceScorer.CurrentTurn(
                "compile artifacts",
                List.of());

        int score = scorer.score(currentTurn,
                new ContextRelevanceScorer.PriorToolResult("grep", "runtime logs only"));

        assertEquals(KeywordContextRelevanceScorer.LOWEST_RELEVANCE_SCORE, score);
    }

    @Test
    void scoresFromTurnTextWithoutRequestedToolCalls() {
        ContextRelevanceScorer.CurrentTurn currentTurn = new ContextRelevanceScorer.CurrentTurn(
                "search logs",
                List.of());

        int score = scorer.score(currentTurn,
                new ContextRelevanceScorer.PriorToolResult("bash", "logs were archived yesterday"));

        assertTrue(score > 0);
    }

    @Test
    void scoresFromRequestedToolNamesWithoutTurnText() {
        ContextRelevanceScorer.CurrentTurn currentTurn = new ContextRelevanceScorer.CurrentTurn(
                "",
                List.of("grep"));

        int score = scorer.score(currentTurn,
                new ContextRelevanceScorer.PriorToolResult("grep", "plain output"));

        assertTrue(score > 0);
    }

    @Test
    void caseDifferencesDoNotAffectMatches() {
        int upperCaseScore = scorer.score(
                new ContextRelevanceScorer.CurrentTurn("BASH", List.of()),
                new ContextRelevanceScorer.PriorToolResult("tool", "bash"));
        int lowerCaseScore = scorer.score(
                new ContextRelevanceScorer.CurrentTurn("bash", List.of()),
                new ContextRelevanceScorer.PriorToolResult("tool", "bash"));

        assertEquals(lowerCaseScore, upperCaseScore);
    }

    @Test
    void punctuationDifferencesDoNotAffectMatches() {
        int hyphenatedScore = scorer.score(
                new ContextRelevanceScorer.CurrentTurn("build-status", List.of()),
                new ContextRelevanceScorer.PriorToolResult("tool", "build status"));
        int spacedScore = scorer.score(
                new ContextRelevanceScorer.CurrentTurn("build status", List.of()),
                new ContextRelevanceScorer.PriorToolResult("tool", "build status"));

        assertEquals(spacedScore, hyphenatedScore);
        assertTrue(hyphenatedScore > 0);
    }

    @Test
    void identicalInputsProduceDeterministicScores() {
        ContextRelevanceScorer.CurrentTurn currentTurn = new ContextRelevanceScorer.CurrentTurn(
                "grep deployment status",
                List.of("grep"));
        ContextRelevanceScorer.PriorToolResult priorToolResult =
                new ContextRelevanceScorer.PriorToolResult("grep", "deployment status is green");

        int firstScore = scorer.score(currentTurn, priorToolResult);
        int secondScore = scorer.score(currentTurn, priorToolResult);
        int thirdScore = scorer.score(currentTurn, priorToolResult);

        assertEquals(firstScore, secondScore);
        assertEquals(secondScore, thirdScore);
    }

    @Test
    void equalScoresAllowStableOrdering() {
        ContextRelevanceScorer.CurrentTurn currentTurn = new ContextRelevanceScorer.CurrentTurn(
                "compile artifacts",
                List.of());

        record Candidate(String id, ContextRelevanceScorer.PriorToolResult result) {}

        List<Candidate> candidates = new ArrayList<>(List.of(
                new Candidate("first", new ContextRelevanceScorer.PriorToolResult("bash", "runtime logs")),
                new Candidate("second", new ContextRelevanceScorer.PriorToolResult("grep", "deployment output"))
        ));

        assertEquals(
                scorer.score(currentTurn, candidates.get(0).result()),
                scorer.score(currentTurn, candidates.get(1).result()));

        candidates.sort(Comparator.comparingInt(
                (Candidate candidate) -> scorer.score(currentTurn, candidate.result())).reversed());

        assertEquals(List.of("first", "second"), candidates.stream().map(Candidate::id).toList());
    }
}
