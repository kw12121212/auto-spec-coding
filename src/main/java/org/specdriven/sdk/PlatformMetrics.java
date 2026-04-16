package org.specdriven.sdk;

/**
 * Snapshot of platform-level metric counters accumulated since the most recent {@code start()}.
 *
 * @param promptTokens      cumulative prompt tokens used (from LLM events)
 * @param completionTokens  cumulative completion tokens used (from LLM events)
 * @param compilationOps    number of skill hot-load operations observed
 * @param llmCacheHits      number of LLM cache hits observed
 * @param llmCacheMisses    number of LLM cache misses observed
 * @param toolCacheHits     number of tool execution cache hits observed
 * @param toolCacheMisses   number of tool execution cache misses observed
 * @param interactionCount  number of interactive command-handled events observed
 * @param snapshotAt        epoch milliseconds at which this snapshot was taken
 */
public record PlatformMetrics(
        long promptTokens,
        long completionTokens,
        long compilationOps,
        long llmCacheHits,
        long llmCacheMisses,
        long toolCacheHits,
        long toolCacheMisses,
        long interactionCount,
        long snapshotAt) {
}
