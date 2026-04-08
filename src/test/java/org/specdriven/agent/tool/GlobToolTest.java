package org.specdriven.agent.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.specdriven.agent.permission.Permission;
import org.specdriven.agent.permission.PermissionContext;
import org.specdriven.agent.permission.PermissionDecision;
import org.specdriven.agent.permission.PermissionProvider;
import org.specdriven.agent.tool.builtin.BuiltinTool;
import org.specdriven.agent.tool.builtin.BuiltinToolManager;

class GlobToolTest {

    private final GlobTool tool = new GlobTool();

    // --- Identity ---

    @Test
    void getName_returnsGlob() {
        assertEquals("glob", tool.getName());
    }

    @Test
    void getDescription_isNonEmpty() {
        assertFalse(tool.getDescription().isBlank());
    }

    // --- Happy path: find files matching glob pattern ---

    @Test
    void findsFilesMatchingGlob(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "content a");
        Files.writeString(tempDir.resolve("b.java"), "content b");
        Files.writeString(tempDir.resolve("c.txt"), "content c");

        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of("pattern", "*.txt"));

        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        String output = ((ToolResult.Success) result).output();
        assertTrue(output.contains("a.txt"));
        assertTrue(output.contains("c.txt"));
        assertFalse(output.contains("b.java"));
    }

    // --- Sorted by modification time ---

    @Test
    void resultsSortedByModificationTime(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path oldFile = tempDir.resolve("old.txt");
        Path newFile = tempDir.resolve("new.txt");
        Files.writeString(oldFile, "old");
        Thread.sleep(50); // ensure different mtime
        Files.writeString(newFile, "new");

        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of("pattern", "*.txt"));

        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        String output = ((ToolResult.Success) result).output();
        int newIdx = output.indexOf("new.txt");
        int oldIdx = output.indexOf("old.txt");
        assertTrue(newIdx < oldIdx, "new.txt should appear before old.txt (most recent first)");
    }

    // --- head_limit ---

    @Test
    void headLimit_capsResults(@TempDir Path tempDir) throws IOException, InterruptedException {
        Files.writeString(tempDir.resolve("a.txt"), "a");
        Thread.sleep(50);
        Files.writeString(tempDir.resolve("b.txt"), "b");
        Thread.sleep(50);
        Files.writeString(tempDir.resolve("c.txt"), "c");

        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of(
                "pattern", "*.txt",
                "head_limit", 2
        ));

        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        String output = ((ToolResult.Success) result).output();
        long lineCount = output.lines().count();
        assertEquals(2, lineCount);
    }

    // --- Missing/empty pattern ---

    @Test
    void missingPattern_returnsError() {
        ToolContext ctx = allowAllContext("/tmp");
        ToolResult result = tool.execute(ToolInput.empty(), ctx);

        assertInstanceOf(ToolResult.Error.class, result);
        assertTrue(((ToolResult.Error) result).message().contains("Missing or empty"));
    }

    @Test
    void emptyPattern_returnsError() {
        ToolContext ctx = allowAllContext("/tmp");
        ToolInput input = new ToolInput(Map.of("pattern", ""));
        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Error.class, result);
        assertTrue(((ToolResult.Error) result).message().contains("Missing or empty"));
    }

    // --- Non-existent path ---

    @Test
    void nonExistentPath_returnsError() {
        ToolContext ctx = allowAllContext("/tmp");
        ToolInput input = new ToolInput(Map.of(
                "pattern", "*.txt",
                "path", "/nonexistent/path/xyz"
        ));
        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Error.class, result);
        assertTrue(((ToolResult.Error) result).message().contains("does not exist or is not a directory"));
    }

    // --- Symlinks skipped ---

    @Test
    void symbolicLinksAreSkipped(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("real.txt"), "content");
        Path link = tempDir.resolve("link.txt");
        Files.createSymbolicLink(link, tempDir.resolve("real.txt"));

        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of("pattern", "*.txt"));

        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        String output = ((ToolResult.Success) result).output();
        assertTrue(output.contains("real.txt"));
        assertFalse(output.contains("link.txt"));
    }

    // --- Subdirectory matching ---

    @Test
    void matchesInSubdirectories(@TempDir Path tempDir) throws IOException {
        Path sub = tempDir.resolve("sub");
        Files.createDirectory(sub);
        Files.writeString(sub.resolve("deep.java"), "code");

        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of("pattern", "**/*.java"));

        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        assertTrue(((ToolResult.Success) result).output().contains("deep.java"));
    }

    // --- No matches ---

    @Test
    void noMatches_returnsEmpty(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "content");

        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of("pattern", "*.java"));

        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        assertTrue(((ToolResult.Success) result).output().isEmpty());
    }

    // --- fd integration: null BuiltinToolManager (no-arg constructor) uses pure Java ---

    @Test
    void noArgConstructor_usesPureJava(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "content");
        GlobTool pureJavaTool = new GlobTool();

        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of("pattern", "*.txt"));

        ToolResult result = pureJavaTool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        assertTrue(((ToolResult.Success) result).output().contains("a.txt"));
    }

    // --- fd integration: fd unavailable falls back to pure Java ---

    @Test
    void fdUnavailable_fallsBackToPureJava(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "content a");
        Files.writeString(tempDir.resolve("b.java"), "content b");

        BuiltinToolManager manager = new StubBuiltinToolManager(Optional.empty());
        GlobTool fdTool = new GlobTool(manager);

        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of("pattern", "*.txt"));

        ToolResult result = fdTool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        String output = ((ToolResult.Success) result).output();
        assertTrue(output.contains("a.txt"));
        assertFalse(output.contains("b.java"));
    }

    // --- fd integration: fd returns error, falls back silently ---

    @Test
    void fdError_fallsBackToPureJava(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "content");

        // Stub returns a path to a non-existent binary, causing process failure
        BuiltinToolManager manager = new StubBuiltinToolManager(Optional.of(Path.of("/nonexistent/fd")));
        GlobTool fdTool = new GlobTool(manager);

        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of("pattern", "*.txt"));

        ToolResult result = fdTool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        assertTrue(((ToolResult.Success) result).output().contains("a.txt"));
    }

    // --- fd integration: fd available and works ---

    @Test
    void fdAvailable_returnsResults(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "content a");
        Files.writeString(tempDir.resolve("b.java"), "content b");

        // Use "fd" on PATH if available; otherwise this test exercises fallback
        BuiltinToolManager manager = new BuiltinToolManager() {
            @Override public Path resolve(BuiltinTool tool) { return Path.of("fd"); }
            @Override public Optional<Path> detect(BuiltinTool tool) { return Optional.of(Path.of("fd")); }
            @Override public Path cacheDir() { return tempDir.resolve("cache"); }
        };
        GlobTool fdTool = new GlobTool(manager);

        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of("pattern", "*.txt"));

        ToolResult result = fdTool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        // If fd is on PATH, it finds a.txt; if not, fallback finds it — either way success
        assertTrue(((ToolResult.Success) result).output().contains("a.txt"));
    }

    // --- Helpers ---

    private static ToolContext allowAllContext(String workDir) {
        PermissionProvider allowAll = new PermissionProvider() {
            @Override public PermissionDecision check(Permission p, PermissionContext c) { return PermissionDecision.ALLOW; }
            @Override public void grant(Permission p, PermissionContext c) {}
            @Override public void revoke(Permission p, PermissionContext c) {}
        };
        return new ToolContext() {
            @Override public String workDir() { return workDir; }
            @Override public PermissionProvider permissionProvider() { return allowAll; }
            @Override public Map<String, String> env() { return Map.of(); }
        };
    }

    /** Stub BuiltinToolManager that returns a fixed detect result. */
    private static class StubBuiltinToolManager implements BuiltinToolManager {
        private final Optional<Path> detected;

        StubBuiltinToolManager(Optional<Path> detected) {
            this.detected = detected;
        }

        @Override public Path resolve(BuiltinTool tool) {
            return detected.orElseThrow();
        }

        @Override public Optional<Path> detect(BuiltinTool tool) {
            return detected;
        }

        @Override public Path cacheDir() {
            return Path.of("/tmp/stub-cache");
        }
    }
}
