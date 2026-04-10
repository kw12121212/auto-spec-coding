package org.specdriven.agent.answer;

import org.specdriven.agent.agent.Message;
import org.specdriven.agent.agent.SystemMessage;
import org.specdriven.agent.question.Question;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Manages the context window for Answer Agent by cropping conversation history.
 *
 * <p>This class extracts the minimum necessary context from a conversation,
 * preserving recent messages and all system messages while limiting the total
 * number of messages to avoid excessive token usage.
 */
public class AnswerContextManager {

    private final int maxContextMessages;

    public AnswerContextManager(int maxContextMessages) {
        if (maxContextMessages <= 0) {
            throw new IllegalArgumentException("maxContextMessages must be positive");
        }
        this.maxContextMessages = maxContextMessages;
    }

    /**
     * Creates a context manager with the default max context messages (10).
     */
    public AnswerContextManager() {
        this(10);
    }

    /**
     * Crops the conversation history to the maximum context size.
     *
     * <p>The cropping strategy:
     * <ol>
     *   <li>Collect all system messages (always preserved)</li>
     *   <li>Take the most recent non-system messages to fill remaining capacity</li>
     *   <li>If the result would be empty, return a fallback with just the question</li>
     * </ol>
     *
     * @param messages the full conversation history
     * @param question the question being asked (for fallback context)
     * @return the cropped message list
     */
    public List<Message> crop(List<Message> messages, Question question) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(question, "question");

        if (messages.isEmpty()) {
            return createFallbackContext(question);
        }

        List<Message> systemMessages = new ArrayList<>();
        List<Message> nonSystemMessages = new ArrayList<>();

        for (Message message : messages) {
            if (message instanceof SystemMessage) {
                systemMessages.add(message);
            } else {
                nonSystemMessages.add(message);
            }
        }

        // Calculate how many non-system messages we can include
        int remainingSlots = maxContextMessages - systemMessages.size();

        if (remainingSlots <= 0) {
            // Only system messages fit
            return systemMessages.isEmpty() ? createFallbackContext(question) : systemMessages;
        }

        // Take the most recent non-system messages
        int startIndex = Math.max(0, nonSystemMessages.size() - remainingSlots);
        List<Message> recentNonSystem = nonSystemMessages.subList(startIndex, nonSystemMessages.size());

        // Combine: system messages first, then recent non-system messages
        List<Message> result = new ArrayList<>(systemMessages.size() + recentNonSystem.size());
        result.addAll(systemMessages);
        result.addAll(recentNonSystem);

        return result.isEmpty() ? createFallbackContext(question) : result;
    }

    /**
     * Creates a fallback context when the normal cropping yields an empty result.
     *
     * @param question the question being asked
     * @return a minimal context containing the question information
     */
    private List<Message> createFallbackContext(Question question) {
        String fallbackContent = String.format(
                "Question: %s\nImpact: %s\nRecommendation: %s",
                question.question(),
                question.impact(),
                question.recommendation()
        );
        return List.of(new SystemMessage(fallbackContent, System.currentTimeMillis()));
    }

    /**
     * Returns the maximum number of context messages.
     */
    public int maxContextMessages() {
        return maxContextMessages;
    }
}
