package org.specdriven.agent.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.specdriven.agent.permission.Permission;
import org.specdriven.agent.permission.PermissionContext;
import org.specdriven.agent.permission.PermissionDecision;
import org.specdriven.agent.permission.PermissionProvider;

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
}
