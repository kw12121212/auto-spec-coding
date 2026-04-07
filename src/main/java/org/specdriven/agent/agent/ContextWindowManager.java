package org.specdriven.agent.agent;

/**
 * Tracks cumulative token usage against a model's context window limit.
 * Not thread-safe — intended for single-threaded per-conversation use.
 */
public class ContextWindowManager {

    private final String modelName;
    private final int maxTokens;
    private int usedTokens;

    /**
     * Creates a new context window manager.
     *
     * @param modelName the model name (for identification)
     * @param maxTokens the model's context window size in tokens
     */
    public ContextWindowManager(String modelName, int maxTokens) {
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
        this.modelName = modelName;
        this.maxTokens = maxTokens;
        this.usedTokens = 0;
    }

    /**
     * Returns the model name.
     */
    public String modelName() {
        return modelName;
    }

    /**
     * Returns the maximum context window size.
     */
    public int maxTokens() {
        return maxTokens;
    }

    /**
     * Returns the number of remaining tokens in the context window.
     */
    public int remainingCapacity() {
        return maxTokens - usedTokens;
    }

    /**
     * Records token usage from an LLM call.
     *
     * @param usage the usage statistics to add
     */
    public void addUsage(LlmUsage usage) {
        if (usage != null) {
            usedTokens += usage.totalTokens();
        }
    }

    /**
     * Checks whether a request with the given estimated token count would fit
     * in the remaining context window.
     *
     * @param estimatedTokens the estimated token count for the request
     * @return true if the request fits
     */
    public boolean canFit(int estimatedTokens) {
        return usedTokens + estimatedTokens <= maxTokens;
    }

    /**
     * Resets all tracked usage.
     */
    public void reset() {
        usedTokens = 0;
    }
}
