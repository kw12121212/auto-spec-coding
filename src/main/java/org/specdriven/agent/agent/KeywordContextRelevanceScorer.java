package org.specdriven.agent.agent;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Default keyword-based scorer for prior tool results.
 */
public final class KeywordContextRelevanceScorer implements ContextRelevanceScorer {

    static final int LOWEST_RELEVANCE_SCORE = 0;
    private static final int EXACT_TOOL_NAME_MATCH_BONUS = 100;
    private static final int KEYWORD_MATCH_WEIGHT = 10;

    @Override
    public int score(CurrentTurn currentTurn, PriorToolResult priorToolResult) {
        CurrentTurn turn = currentTurn == null ? new CurrentTurn("", java.util.List.of()) : currentTurn;
        PriorToolResult result = priorToolResult == null ? new PriorToolResult("", "") : priorToolResult;

        Set<String> turnTextKeywords = tokenize(turn.text());
        Set<String> requestedToolKeywords = new LinkedHashSet<>();
        Set<String> normalizedRequestedToolNames = new LinkedHashSet<>();
        for (String requestedToolName : turn.requestedToolNames()) {
            String normalizedRequestedToolName = normalizePhrase(requestedToolName);
            if (!normalizedRequestedToolName.isEmpty()) {
                normalizedRequestedToolNames.add(normalizedRequestedToolName);
            }
            requestedToolKeywords.addAll(tokenize(requestedToolName));
        }

        Set<String> resultTextKeywords = tokenize(result.content());
        Set<String> resultToolKeywords = tokenize(result.toolName());

        int score = LOWEST_RELEVANCE_SCORE;
        String normalizedToolName = normalizePhrase(result.toolName());
        if (!normalizedToolName.isEmpty() && normalizedRequestedToolNames.contains(normalizedToolName)) {
            score += EXACT_TOOL_NAME_MATCH_BONUS;
        }

        score += countOverlap(turnTextKeywords, resultTextKeywords) * KEYWORD_MATCH_WEIGHT;
        score += countOverlap(turnTextKeywords, resultToolKeywords) * KEYWORD_MATCH_WEIGHT;
        score += countOverlap(requestedToolKeywords, resultTextKeywords) * KEYWORD_MATCH_WEIGHT;

        return score;
    }

    private static int countOverlap(Set<String> left, Set<String> right) {
        int matches = 0;
        for (String keyword : left) {
            if (right.contains(keyword)) {
                matches++;
            }
        }
        return matches;
    }

    private static Set<String> tokenize(String value) {
        String normalized = normalizePhrase(value);
        if (normalized.isEmpty()) {
            return Set.of();
        }

        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String token : normalized.split(" ")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static String normalizePhrase(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        StringBuilder normalized = new StringBuilder(value.length());
        boolean lastWasSeparator = true;
        for (char character : value.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(character)) {
                normalized.append(character);
                lastWasSeparator = false;
            } else if (!lastWasSeparator) {
                normalized.append(' ');
                lastWasSeparator = true;
            }
        }

        int length = normalized.length();
        if (length > 0 && normalized.charAt(length - 1) == ' ') {
            normalized.setLength(length - 1);
        }
        return normalized.toString();
    }
}
