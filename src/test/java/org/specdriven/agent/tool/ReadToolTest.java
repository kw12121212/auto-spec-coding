package org.specdriven.agent.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.specdriven.agent.permission.Permission;
import org.specdriven.agent.permission.PermissionContext;
import org.specdriven.agent.permission.PermissionDecision;
import org.specdriven.agent.permission.PermissionProvider;

class ReadToolTest {

    private final ReadTool tool = new ReadTool();

    // --- Identity tests ---

    @Test
    void getName_returnsRead() {
        assertEquals("read", tool.getName());
    }

    @Test
    void getDescription_isNonEmpty() {
        assertFalse(tool.getDescription().isBlank());
    }

    @Test
    void getParameters_declaresPathOffsetLimit() {
        List<ToolParameter> params = tool.getParameters();
        assertEquals(3, params.size());
        assertEquals("path", params.get(0).name());
        assertTrue(params.get(0).required());
        assertEquals("offset", params.get(1).name());
        assertFalse(params.get(1).required());
        assertEquals("limit", params.get(2).name());
        assertFalse(params.get(2).required());
    }

    // --- Happy path ---

    @Test
    void readFullFile_returnsSuccess(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello world");

        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of("path", file.toString()));
        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        assertEquals("hello world", ((ToolResult.Success) result).output());
    }

    @Test
    void readWithOffsetAndLimit_returnsLineRange(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("lines.txt");
        Files.writeString(file, "line1\nline2\nline3\nline4\nline5");

        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of(
                "path", file.toString(),
                "offset", 2,
                "limit", 2
        ));
        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        assertEquals("line2\nline3", ((ToolResult.Success) result).output());
    }

    @Test
    void readWithOffsetOnly_returnsFromOffsetToEnd(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("lines.txt");
        Files.writeString(file, "line1\nline2\nline3");

        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of(
                "path", file.toString(),
                "offset", 2
        ));
        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        assertEquals("line2\nline3", ((ToolResult.Success) result).output());
    }

    @Test
    void readWithLimitOnly_returnsFirstNLines(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("lines.txt");
        Files.writeString(file, "line1\nline2\nline3\nline4");

        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of(
                "path", file.toString(),
                "limit", 2
        ));
        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        assertEquals("line1\nline2", ((ToolResult.Success) result).output());
    }

    @Test
    void readRelativePath_resolvesAgainstWorkDir(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("rel.txt");
        Files.writeString(file, "relative content");

        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of("path", "rel.txt"));
        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        assertEquals("relative content", ((ToolResult.Success) result).output());
    }

    // --- Error cases ---

    @Test
    void missingFile_returnsError(@TempDir Path tempDir) {
        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of("path", tempDir.resolve("nonexistent.txt").toString()));
        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Error.class, result);
        assertTrue(((ToolResult.Error) result).message().contains("Failed to read file"));
    }

    @Test
    void missingPathParam_returnsError() {
        ToolContext ctx = allowAllContext("/tmp");
        ToolResult result = tool.execute(ToolInput.empty(), ctx);

        assertInstanceOf(ToolResult.Error.class, result);
        assertTrue(((ToolResult.Error) result).message().contains("Missing or empty"));
    }

    @Test
    void emptyPathParam_returnsError() {
        ToolContext ctx = allowAllContext("/tmp");
        ToolInput input = new ToolInput(Map.of("path", "  "));
        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Error.class, result);
        assertTrue(((ToolResult.Error) result).message().contains("Missing or empty"));
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
