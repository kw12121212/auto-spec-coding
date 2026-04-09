package org.specdriven.agent.tool.cache;

import org.specdriven.agent.permission.Permission;
import org.specdriven.agent.tool.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Decorator that transparently adds caching to a delegate {@link Tool}.
 * <p>
 * On {@link #execute(ToolInput, ToolContext)}: generates a cache key from tool name + parameters,
 * checks the cache, and either returns the cached result or delegates and caches the response.
 * Only caches {@link ToolResult.Success}; errors are never cached.
 * For the "read" tool, file-change invalidation is applied on cache hit.
 */
public class CachingTool implements Tool {

    private static final System.Logger LOG =
            System.getLogger(CachingTool.class.getName());

    private final Tool delegate;
    private final ToolCache cache;
    private final long ttlMs;

    /**
     * Creates a caching decorator around the given delegate.
     *
     * @param delegate the real tool to delegate to on cache misses
     * @param cache    the cache backend
     * @param ttlMs    time-to-live in milliseconds for cached entries
     */
    public CachingTool(Tool delegate, ToolCache cache, long ttlMs) {
        this.delegate = delegate;
        this.cache = cache;
        this.ttlMs = ttlMs;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }

    @Override
    public List<ToolParameter> getParameters() {
        return delegate.getParameters();
    }

    @Override
    public ToolResult execute(ToolInput input, ToolContext context) {
        String key = ToolCacheKey.generate(getName(), input.parameters());

        // Check cache
        try {
            var cached = cache.get(key);
            if (cached.isPresent()) {
                ToolCache.CacheEntry entry = cached.get();

                // For "read" tool, validate file freshness
                if ("read".equals(getName()) && entry.filePath() != null) {
                    long currentModified = getFileModified(entry.filePath());
                    if (currentModified != entry.fileModified()) {
                        // File changed or deleted — invalidate and re-execute
                        cache.invalidate(key);
                    } else {
                        return new ToolResult.Success(entry.output());
                    }
                } else {
                    return new ToolResult.Success(entry.output());
                }
            }
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Cache lookup failed, falling back to delegate", e);
        }

        // Cache miss — call delegate
        ToolResult result = delegate.execute(input, context);

        // Only cache Success results
        if (result instanceof ToolResult.Success success) {
            try {
                String filePath = extractFilePath(input);
                long fileModified = getFileModified(filePath);
                cache.put(key, success.output(), ttlMs, filePath, fileModified);
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING, "Cache storage failed", e);
            }
        }

        return result;
    }

    @Override
    public Permission permissionFor(ToolInput input, ToolContext context) {
        return delegate.permissionFor(input, context);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private String extractFilePath(ToolInput input) {
        Object path = input.parameters().get("path");
        return path != null ? path.toString() : null;
    }

    private long getFileModified(String filePath) {
        if (filePath == null || filePath.isEmpty()) return 0;
        try {
            Path p = Path.of(filePath);
            return Files.getLastModifiedTime(p).toMillis();
        } catch (Exception e) {
            return 0; // file doesn't exist
        }
    }
}
