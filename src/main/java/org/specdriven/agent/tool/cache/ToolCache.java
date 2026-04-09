package org.specdriven.agent.tool.cache;

import java.util.Optional;

/**
 * Interface for tool execution result caching.
 */
public interface ToolCache {

    /**
     * Cached entry including output and file-change metadata.
     *
     * @param output       the cached tool output
     * @param filePath     the file path associated with this entry (may be null)
     * @param fileModified the file's lastModified when cached (may be 0)
     */
    record CacheEntry(String output, String filePath, long fileModified) {}

    /**
     * Retrieves a cached entry by key.
     *
     * @param key the cache key
     * @return the cached entry, or empty if not found or expired
     */
    Optional<CacheEntry> get(String key);

    /**
     * Stores a tool output in the cache with a TTL.
     *
     * @param key          the cache key
     * @param output       the tool output to cache
     * @param ttlMs        time-to-live in milliseconds
     * @param filePath     optional file path for file-change invalidation (may be null)
     * @param fileModified optional file last-modified timestamp (may be 0)
     */
    void put(String key, String output, long ttlMs, String filePath, long fileModified);

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
}
