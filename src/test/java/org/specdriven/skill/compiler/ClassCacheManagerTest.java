package org.specdriven.skill.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassCacheManagerTest {

    private static final byte[] STUB_CLASS_BYTES = {
            (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE
    };

    @TempDir
    Path tempDir;

    @Test
    void cacheMissReturnsFalseFromIsCached() {
        ClassCacheManager cache = new LealoneClassCacheManager(tempDir);

        assertFalse(cache.isCached("demo.HelloSkill", "abc123"));
    }

    @Test
    void cacheHitReturnsTrueAfterClassFileWritten() throws Exception {
        ClassCacheManager cache = new LealoneClassCacheManager(tempDir);
        Path slotDir = cache.resolveClassDir("demo.HelloSkill", "abc123");
        Files.write(slotDir.resolve("HelloSkill.class"), STUB_CLASS_BYTES);

        assertTrue(cache.isCached("demo.HelloSkill", "abc123"));
    }

    @Test
    void emptyDirectoryIsNotACacheHit() {
        ClassCacheManager cache = new LealoneClassCacheManager(tempDir);
        cache.resolveClassDir("demo.HelloSkill", "abc123");

        assertFalse(cache.isCached("demo.HelloSkill", "abc123"));
    }

    @Test
    void resolveClassDirCreatesDirectoryAndReturnsConsistentPath() {
        ClassCacheManager cache = new LealoneClassCacheManager(tempDir);

        Path dir1 = cache.resolveClassDir("demo.HelloSkill", "abc123");
        Path dir2 = cache.resolveClassDir("demo.HelloSkill", "abc123");

        assertEquals(dir1, dir2);
        assertTrue(Files.isDirectory(dir1));
        assertEquals(tempDir.resolve("demo.HelloSkill").resolve("abc123"), dir1);
    }

    @Test
    void resolveClassDirCreatesNestedDirectories() {
        ClassCacheManager cache = new LealoneClassCacheManager(tempDir);

        Path dir = cache.resolveClassDir("org.example.skills.MySkill", "deadbeef");

        assertTrue(Files.isDirectory(dir));
        assertEquals(tempDir.resolve("org.example.skills.MySkill").resolve("deadbeef"), dir);
    }

    @Test
    void loadCachedReturnsClassLoaderThatLoadsCompiledClass() throws Exception {
        ClassCacheManager cache = new LealoneClassCacheManager(tempDir);
        SkillSourceCompiler compiler = new LealoneSkillSourceCompiler();
        Path slotDir = cache.resolveClassDir("demo.HelloSkill", "abc123");
        compiler.compile(
                "demo.HelloSkill",
                "package demo; public class HelloSkill { public static String run() { return \"ok\"; } }",
                slotDir);

        ClassLoader loader = cache.loadCached("demo.HelloSkill", "abc123");

        assertNotNull(loader);
        Class<?> cls = loader.loadClass("demo.HelloSkill");
        assertNotNull(cls);
        assertEquals("demo.HelloSkill", cls.getName());
    }

    @Test
    void loadCachedThrowsClassCacheExceptionOnMiss() {
        ClassCacheManager cache = new LealoneClassCacheManager(tempDir);

        assertThrows(ClassCacheException.class,
                () -> cache.loadCached("demo.MissingSkill", "xyz789"));
    }

    @Test
    void invalidateRemovesCacheSlot() throws Exception {
        ClassCacheManager cache = new LealoneClassCacheManager(tempDir);
        Path slotDir = cache.resolveClassDir("demo.HelloSkill", "abc123");
        Files.write(slotDir.resolve("HelloSkill.class"), STUB_CLASS_BYTES);
        assertTrue(cache.isCached("demo.HelloSkill", "abc123"));

        cache.invalidate("demo.HelloSkill", "abc123");

        assertFalse(cache.isCached("demo.HelloSkill", "abc123"));
        assertFalse(Files.exists(slotDir));
    }

    @Test
    void invalidateIsNoOpWhenEntryAbsent() {
        ClassCacheManager cache = new LealoneClassCacheManager(tempDir);

        assertDoesNotThrow(() -> cache.invalidate("demo.NonExistentSkill", "abc123"));
    }
}
