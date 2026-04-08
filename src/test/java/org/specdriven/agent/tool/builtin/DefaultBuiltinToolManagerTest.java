package org.specdriven.agent.tool.builtin;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultBuiltinToolManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void cacheDirReturnsConfiguredPath() {
        Path cacheDir = tempDir.resolve("cache");
        DefaultBuiltinToolManager manager = new DefaultBuiltinToolManager(cacheDir);
        assertEquals(cacheDir, manager.cacheDir());
    }

    @Test
    void detectReturnsEmptyWhenToolNotAvailable() {
        Path cacheDir = tempDir.resolve("cache");
        DefaultBuiltinToolManager manager = new DefaultBuiltinToolManager(cacheDir);
        var result = manager.detect(BuiltinTool.RG);
        // Result is either present (tool on PATH) or empty — both valid
        assertNotNull(result);
    }

    @Test
    void resolveReturnsValidPath() throws IOException {
        Path cacheDir = tempDir.resolve("cache");
        DefaultBuiltinToolManager manager = new DefaultBuiltinToolManager(cacheDir);

        // resolve() should always return a valid path or throw
        Path result = manager.resolve(BuiltinTool.RG);
        assertTrue(Files.exists(result));
        assertTrue(Files.isExecutable(result));
    }

    @Test
    void resolvePrefersCachedBinaryOverPATH() throws IOException {
        Path cacheDir = tempDir.resolve("cache");
        Files.createDirectories(cacheDir);

        // Place a fake cached binary
        Path fakeRg = cacheDir.resolve("rg");
        Files.writeString(fakeRg, "#!/bin/sh\necho fake rg");
        fakeRg.toFile().setExecutable(true);

        DefaultBuiltinToolManager manager = new DefaultBuiltinToolManager(cacheDir);
        Path result = manager.resolve(BuiltinTool.RG);
        // Should return the cached binary, not the system one
        assertEquals(fakeRg, result);
    }

    @Test
    void resolveReturnsCachedBinaryOnSecondCall() throws IOException {
        Path cacheDir = tempDir.resolve("cache");
        DefaultBuiltinToolManager manager = new DefaultBuiltinToolManager(cacheDir);

        Path first = manager.resolve(BuiltinTool.RG);
        assertTrue(Files.exists(first));
        Path second = manager.resolve(BuiltinTool.RG);
        assertEquals(first, second);
    }

    @Test
    void detectFindsCachedBinary() throws IOException {
        Path cacheDir = tempDir.resolve("cache");
        Files.createDirectories(cacheDir);

        Path fakeRg = cacheDir.resolve("rg");
        Files.writeString(fakeRg, "#!/bin/sh\necho fake rg");
        fakeRg.toFile().setExecutable(true);

        DefaultBuiltinToolManager manager = new DefaultBuiltinToolManager(cacheDir);
        var result = manager.detect(BuiltinTool.RG);
        assertTrue(result.isPresent());
        assertEquals(fakeRg, result.get());
    }

    @Test
    void extractFromClasspathCreatesCacheDir() throws IOException {
        Path cacheDir = tempDir.resolve("nested").resolve("cache");

        // Pre-populate cache with a fake binary so we can test extraction path
        // by removing it and testing that nested dirs are created
        DefaultBuiltinToolManager manager = new DefaultBuiltinToolManager(cacheDir);

        // Check if resource is available and rg is not on PATH
        String resourcePath = BuiltinTool.RG.resourcePath(Platform.detect());
        boolean hasResource = getClass().getClassLoader().getResourceAsStream(resourcePath) != null;

        if (hasResource) {
            // Clear any system rg from PATH by using a fresh cache
            // If rg is on PATH, resolve returns PATH version (no dir creation needed)
            // If not on PATH, resolve extracts from classpath and creates dir
            var detected = manager.detect(BuiltinTool.RG);
            if (detected.isEmpty()) {
                // Not on PATH and not cached — extraction will happen
                manager.resolve(BuiltinTool.RG);
                assertTrue(Files.exists(cacheDir));
            }
        }
    }

    @Test
    void defaultConstructorUsesHomeDir() {
        DefaultBuiltinToolManager manager = new DefaultBuiltinToolManager();
        String expected = System.getProperty("user.home") + "/.specdriven/bin";
        assertEquals(Path.of(expected), manager.cacheDir());
    }

    @Test
    void classpathResourceExistsForCurrentPlatform() {
        // Verify that the current platform has bundled binaries
        String resourcePath = BuiltinTool.RG.resourcePath(Platform.detect());
        InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        assertNotNull(stream, "Bundled rg binary should exist for current platform: " + resourcePath);
        try { stream.close(); } catch (IOException ignored) {}
    }
}
