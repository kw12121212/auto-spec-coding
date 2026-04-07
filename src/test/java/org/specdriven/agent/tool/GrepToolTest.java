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

class GrepToolTest {

    private final GrepTool tool = new GrepTool();

    // --- Identity ---

    @Test
    void getName_returnsGrep() {
        assertEquals("grep", tool.getName());
    }

    @Test
    void getDescription_isNonEmpty() {
        assertFalse(tool.getDescription().isBlank());
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

    // --- Invalid regex ---

    @Test
    void invalidRegex_returnsError() {
        ToolContext ctx = allowAllContext("/tmp");
        ToolInput input = new ToolInput(Map.of("pattern", "[invalid"));
        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Error.class, result);
        assertTrue(((ToolResult.Error) result).message().contains("Invalid regex"));
    }

    // --- Non-existent path ---

    @Test
    void nonExistentPath_returnsError() {
        ToolContext ctx = allowAllContext("/tmp");
        ToolInput input = new ToolInput(Map.of(
                "pattern", "test",
                "path", "/nonexistent/path/xyz"
        ));
        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Error.class, result);
        assertTrue(((ToolResult.Error) result).message().contains("does not exist or is not a directory"));
    }

    // --- Content mode ---

    @Test
    void contentMode_showsPathLineNumberAndContent(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "hello world\nfoo bar\nhello again");
        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of("pattern", "hello"));

        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        String output = ((ToolResult.Success) result).output();
        assertTrue(output.contains("a.txt:1:hello world"));
        assertTrue(output.contains("a.txt:3:hello again"));
    }

    @Test
    void contentMode_withContextLines(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "line1\nline2\nmatch line\nline4\nline5");
        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of(
                "pattern", "match",
                "context", 1
        ));

        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        String output = ((ToolResult.Success) result).output();
        // Context before: path-lineNumber-content
        assertTrue(output.contains("a.txt-2-line2"));
        // Match: path:lineNumber:content
        assertTrue(output.contains("a.txt:3:match line"));
        // Context after: path-lineNumber-content
        assertTrue(output.contains("a.txt-4-line4"));
    }

    // --- files_with_matches mode ---

    @Test
    void filesWithMatchesMode_returnsFilePaths(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "hello");
        Files.writeString(tempDir.resolve("b.txt"), "no match here");
        Files.writeString(tempDir.resolve("c.txt"), "hello again");
        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of(
                "pattern", "hello",
                "output_mode", "files_with_matches"
        ));

        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        String output = ((ToolResult.Success) result).output();
        assertTrue(output.contains("a.txt"));
        assertTrue(output.contains("c.txt"));
        assertFalse(output.contains("b.txt"));
    }

    // --- count mode ---

    @Test
    void countMode_returnsPathAndCount(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "hello\nworld\nhello");
        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of(
                "pattern", "hello",
                "output_mode", "count"
        ));

        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        String output = ((ToolResult.Success) result).output();
        assertTrue(output.contains("a.txt:2"));
    }

    // --- Case insensitive ---

    @Test
    void caseInsensitive_matchesUpperCase(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "Hello World");
        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of(
                "pattern", "hello",
                "case_insensitive", true
        ));

        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        String output = ((ToolResult.Success) result).output();
        assertTrue(output.contains("a.txt:1:Hello World"));
    }

    @Test
    void caseSensitive_doesNotMatchUpperCase(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "Hello World");
        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of("pattern", "hello"));

        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        String output = ((ToolResult.Success) result).output();
        assertTrue(output.isEmpty());
    }

    // --- Glob filtering ---

    @Test
    void globFilter_onlySearchedMatchingFiles(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a.java"), "import hello;");
        Files.writeString(tempDir.resolve("a.txt"), "hello");
        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of(
                "pattern", "hello",
                "glob", "*.java"
        ));

        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        String output = ((ToolResult.Success) result).output();
        assertTrue(output.contains("a.java"));
        assertFalse(output.contains("a.txt"));
    }

    // --- head_limit ---

    @Test
    void headLimit_capsResults(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "match\nmatch\nmatch\nmatch\nmatch");
        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of(
                "pattern", "match",
                "head_limit", 2
        ));

        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        String output = ((ToolResult.Success) result).output();
        long lineCount = output.lines().count();
        assertEquals(2, lineCount);
    }

    // --- Binary files skipped ---

    @Test
    void binaryFilesAreSkipped(@TempDir Path tempDir) throws IOException {
        byte[] binaryData = new byte[100];
        binaryData[10] = 0; // null byte
        Files.write(tempDir.resolve("binary.dat"), binaryData);
        Files.writeString(tempDir.resolve("text.txt"), "hello");
        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of(
                "pattern", "hello",
                "output_mode", "files_with_matches"
        ));

        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        String output = ((ToolResult.Success) result).output();
        assertFalse(output.contains("binary.dat"));
        assertTrue(output.contains("text.txt"));
    }

    // --- Unreadable files silently skipped ---

    @Test
    void unreadableFilesSilentlySkipped(@TempDir Path tempDir) throws IOException {
        // Create a file and a subdirectory with a file
        Path subDir = tempDir.resolve("sub");
        Files.createDirectory(subDir);
        Files.writeString(subDir.resolve("readable.txt"), "hello world");
        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of("pattern", "hello"));

        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        // Should succeed without error, having searched readable files
        assertTrue(((ToolResult.Success) result).output().contains("hello world"));
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
