package org.specdriven.agent.llm;

import org.specdriven.agent.agent.LlmUsage;

import java.util.List;
import java.util.Optional;

/**
 * Interface for LLM response caching and token usage persistence.
 */
public interface LlmCache {

    /**
     * Retrieves a cached response JSON by key.
     *
     * @param key the cache key
     * @return the cached response JSON, or empty if not found or expired
     */
    Optional<String> get(String key);

    /**
     * Stores a response JSON in the cache with a TTL.
     *
     * @param key          the cache key
     * @param responseJson the serialized LLM response
     * @param ttlMs        time-to-live in milliseconds
     */
    void put(String key, String responseJson, long ttlMs);

    /**
     * Removes a cached entry by key.
     *
     * @param key the cache key to invalidate
     */
    void invalidate(String key);

    /**
     * Removes all cached entries.
     */
    void clear();

    /**
     * Records token usage for an LLM call.
     *
     * @param sessionId  the session identifier (may be null)
     * @param agentName  the agent name (may be null)
     * @param model      the model identifier
     * @param usage      the token usage
     */
    void recordUsage(String sessionId, String agentName, String model, LlmUsage usage);

    /**
     * Queries recorded token usage with optional filters.
     * Null parameters are treated as "no filter" (match all).
     *
     * @param sessionId  filter by session ID, or null for all
     * @param agentName  filter by agent name, or null for all
     * @param from       inclusive lower bound for created_at, or 0 for no lower bound
     * @param to         inclusive upper bound for created_at, or Long.MAX_VALUE for no upper bound
     * @return list of matching usage records, never null
     */
    List<UsageRecord> queryUsage(String sessionId, String agentName, long from, long to);
}
