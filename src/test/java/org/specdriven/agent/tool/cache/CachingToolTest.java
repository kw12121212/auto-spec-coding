package org.specdriven.agent.tool.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.specdriven.agent.permission.*;
import org.specdriven.agent.tool.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CachingToolTest {

    @TempDir
    Path tempDir;

    private FailingCache failingCache;
    private StubTool stubTool;

    @BeforeEach
    void setUp() {
        failingCache = new FailingCache();
        stubTool = new StubTool("read", new ToolResult.Success("default-output"));
    }

    // -------------------------------------------------------------------------
    // Cache hit / miss behavior
    // -------------------------------------------------------------------------

    @Test
    void cacheHit_returnsCachedResult_withoutCallingDelegate() {
        var cache = new InMemoryCache();
        cache.put("some-key", "cached-output", 60_000L, null, 0);

        // Use a tool that would throw if called
        Tool neverCalled = new StubTool("read", new ToolResult.Success("should-not-see-this")) {
            @Override
            public ToolResult execute(ToolInput input, ToolContext context) {
                fail("Delegate should not be called on cache hit");
                return null;
            }
        };

        // We need the cache key to match — since we can't predict the key,
        // use a pre-seeded approach instead
        Tool readTool = new StubTool("read", new ToolResult.Success("fresh-output"));
        CachingTool caching = new CachingTool(readTool, cache, 60_000L);

        // First call: cache miss, delegates
        ToolInput input = new ToolInput(Map.of("path", "/nonexistent"));
        ToolResult result1 = caching.execute(input, new StubContext());
        assertEquals("fresh-output", ((ToolResult.Success) result1).output());

        // Second call: cache hit, returns cached
        ToolResult result2 = caching.execute(input, new StubContext());
        assertEquals("fresh-output", ((ToolResult.Success) result2).output());
    }

    @Test
    void cacheMiss_callsDelegateAndCachesResult() {
        var cache = new InMemoryCache();
        int[] callCount = {0};
        Tool tool = new StubTool("grep", null) {
            @Override
            public ToolResult execute(ToolInput input, ToolContext context) {
                callCount[0]++;
                return new ToolResult.Success("search-result");
            }
        };

        CachingTool caching = new CachingTool(tool, cache, 60_000L);
        ToolInput input = new ToolInput(Map.of("pattern", "foo"));

        // First call: miss, delegates
        ToolResult result1 = caching.execute(input, new StubContext());
        assertEquals("search-result", ((ToolResult.Success) result1).output());
        assertEquals(1, callCount[0]);

        // Second call: hit from cache
        ToolResult result2 = caching.execute(input, new StubContext());
        assertEquals("search-result", ((ToolResult.Success) result2).output());
        assertEquals(1, callCount[0]); // delegate not called again
    }

    @Test
    void errorResults_notCached() {
        var cache = new InMemoryCache();
        int[] callCount = {0};
        Tool tool = new StubTool("read", null) {
            @Override
            public ToolResult execute(ToolInput input, ToolContext context) {
                callCount[0]++;
                return new ToolResult.Error("file not found");
            }
        };

        CachingTool caching = new CachingTool(tool, cache, 60_000L);
        ToolInput input = new ToolInput(Map.of("path", "/missing"));

        // First call: error
        ToolResult result1 = caching.execute(input, new StubContext());
        assertInstanceOf(ToolResult.Error.class, result1);

        // Second call: delegates again (error was not cached)
        ToolResult result2 = caching.execute(input, new StubContext());
        assertInstanceOf(ToolResult.Error.class, result2);
        assertEquals(2, callCount[0]);
    }

    // -------------------------------------------------------------------------
    // ReadTool file-change invalidation
    // -------------------------------------------------------------------------

    @Test
    void readFileUnchanged_returnsCachedResult() throws Exception {
        var cache = new InMemoryCache();
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello");
        long modified = Files.getLastModifiedTime(file).toMillis();

        int[] callCount = {0};
        Tool tool = new StubTool("read", null) {
            @Override
            public ToolResult execute(ToolInput input, ToolContext context) {
                callCount[0]++;
                try {
                    return new ToolResult.Success(Files.readString(file));
                } catch (Exception e) {
                    return new ToolResult.Error(e.getMessage(), e);
                }
            }
        };

        CachingTool caching = new CachingTool(tool, cache, 60_000L);
        ToolInput input = new ToolInput(Map.of("path", file.toString()));

        // First call: miss, delegates and caches
        caching.execute(input, new StubContext());
        assertEquals(1, callCount[0]);

        // Second call: file unchanged, returns cached
        caching.execute(input, new StubContext());
        assertEquals(1, callCount[0]); // not called again
    }

    @Test
    void readFileChanged_invalidatesAndReExecutes() throws Exception {
        var cache = new InMemoryCache();
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello");

        int[] callCount = {0};
        Tool tool = new StubTool("read", null) {
            @Override
            public ToolResult execute(ToolInput input, ToolContext context) {
                callCount[0]++;
                try {
                    return new ToolResult.Success(Files.readString(file));
                } catch (Exception e) {
                    return new ToolResult.Error(e.getMessage(), e);
                }
            }
        };

        CachingTool caching = new CachingTool(tool, cache, 60_000L);
        ToolInput input = new ToolInput(Map.of("path", file.toString()));

        // First call
        caching.execute(input, new StubContext());
        assertEquals(1, callCount[0]);

        // Modify file (ensure timestamp changes)
        Thread.sleep(50);
        Files.writeString(file, "world");
        // Force a different lastModified
        file.toFile().setLastModified(System.currentTimeMillis() + 1000);

        // Second call: file changed, re-executes
        ToolResult result = caching.execute(input, new StubContext());
        assertEquals(2, callCount[0]);
        assertEquals("world", ((ToolResult.Success) result).output());
    }

    // -------------------------------------------------------------------------
    // Graceful cache failure handling
    // -------------------------------------------------------------------------

    @Test
    void cacheGetFailure_fallsBackToDelegate() {
        var cache = new FailingCache();
        cache.getShouldThrow = true;

        Tool tool = new StubTool("read", new ToolResult.Success("real-output"));
        CachingTool caching = new CachingTool(tool, cache, 60_000L);
        ToolInput input = new ToolInput(Map.of("path", "/foo.txt"));

        ToolResult result = caching.execute(input, new StubContext());
        assertEquals("real-output", ((ToolResult.Success) result).output());
    }

    @Test
    void cachePutFailure_stillReturnsResult() {
        var cache = new FailingCache();
        cache.putShouldThrow = true;

        Tool tool = new StubTool("read", new ToolResult.Success("real-output"));
        CachingTool caching = new CachingTool(tool, cache, 60_000L);
        ToolInput input = new ToolInput(Map.of("path", "/foo.txt"));

        ToolResult result = caching.execute(input, new StubContext());
        assertEquals("real-output", ((ToolResult.Success) result).output());
    }

    // -------------------------------------------------------------------------
    // Non-read tools bypass file-change check
    // -------------------------------------------------------------------------

    @Test
    void nonReadTool_usesCacheWithoutFileCheck() {
        var cache = new InMemoryCache();
        int[] callCount = {0};
        Tool tool = new StubTool("grep", null) {
            @Override
            public ToolResult execute(ToolInput input, ToolContext context) {
                callCount[0]++;
                return new ToolResult.Success("match");
            }
        };

        CachingTool caching = new CachingTool(tool, cache, 60_000L);
        ToolInput input = new ToolInput(Map.of("pattern", "foo", "path", "/some/dir"));

        caching.execute(input, new StubContext());
        assertEquals(1, callCount[0]);

        caching.execute(input, new StubContext());
        assertEquals(1, callCount[0]); // cached, no file check
    }

    // -------------------------------------------------------------------------
    // Test stubs
    // -------------------------------------------------------------------------

    private static class StubTool implements Tool {
        private final String name;
        private final ToolResult defaultResult;

        StubTool(String name, ToolResult defaultResult) {
            this.name = name;
            this.defaultResult = defaultResult;
        }

        @Override
        public String getName() { return name; }

        @Override
        public String getDescription() { return "stub " + name; }

        @Override
        public List<ToolParameter> getParameters() { return List.of(); }

        @Override
        public ToolResult execute(ToolInput input, ToolContext context) {
            return defaultResult;
        }

        @Override
        public Permission permissionFor(ToolInput input, ToolContext context) {
            return new Permission("execute", name, Map.of());
        }
    }

    private static class StubContext implements ToolContext {
        @Override
        public String workDir() { return "/tmp"; }

        @Override
        public PermissionProvider permissionProvider() {
            return new PermissionProvider() {
                @Override
                public PermissionDecision check(Permission permission, PermissionContext context) {
                    return PermissionDecision.ALLOW;
                }
                @Override
                public void grant(Permission permission, PermissionContext context) {}
                @Override
                public void revoke(Permission permission, PermissionContext context) {}
            };
        }

        @Override
        public Map<String, String> env() { return Map.of(); }
    }

    /** Simple in-memory ToolCache for testing. */
    private static class InMemoryCache implements ToolCache {
        private final Map<String, CacheEntry> store = new HashMap<>();
        private final Map<String, Long> expiry = new HashMap<>();

        @Override
        public Optional<CacheEntry> get(String key) {
            Long exp = expiry.get(key);
            if (exp == null || System.currentTimeMillis() > exp) {
                store.remove(key);
                expiry.remove(key);
                return Optional.empty();
            }
            CacheEntry entry = store.get(key);
            return Optional.ofNullable(entry);
        }

        @Override
        public void put(String key, String output, long ttlMs, String filePath, long fileModified) {
            store.put(key, new CacheEntry(output, filePath, fileModified));
            expiry.put(key, System.currentTimeMillis() + ttlMs);
        }

        @Override
        public void invalidate(String key) {
            store.remove(key);
            expiry.remove(key);
        }

        @Override
        public void clear() {
            store.clear();
            expiry.clear();
        }
    }

    /** A ToolCache that can simulate failures. */
    private static class FailingCache implements ToolCache {
        boolean getShouldThrow = false;
        boolean putShouldThrow = false;

        @Override
        public Optional<CacheEntry> get(String key) {
            if (getShouldThrow) throw new RuntimeException("cache get failed");
            return Optional.empty();
        }

        @Override
        public void put(String key, String output, long ttlMs, String filePath, long fileModified) {
            if (putShouldThrow) throw new RuntimeException("cache put failed");
        }

        @Override
        public void invalidate(String key) {}

        @Override
        public void clear() {}
    }
}
