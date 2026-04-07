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

class WriteToolTest {

    private final WriteTool tool = new WriteTool();

    // --- Identity tests ---

    @Test
    void getName_returnsWrite() {
        assertEquals("write", tool.getName());
    }

    @Test
    void getDescription_isNonEmpty() {
        assertFalse(tool.getDescription().isBlank());
    }

    @Test
    void getParameters_declaresPathContent() {
        List<ToolParameter> params = tool.getParameters();
        assertEquals(2, params.size());
        assertEquals("path", params.get(0).name());
        assertTrue(params.get(0).required());
        assertEquals("content", params.get(1).name());
        assertTrue(params.get(1).required());
    }

    // --- Happy path ---

    @Test
    void createNewFile_returnsSuccess(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("new.txt");

        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of("path", file.toString(), "content", "hello"));
        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        assertEquals("hello", Files.readString(file));
    }

    @Test
    void overwriteExisting_returnsSuccess(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("existing.txt");
        Files.writeString(file, "old content");

        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of("path", file.toString(), "content", "new content"));
        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        assertEquals("new content", Files.readString(file));
    }

    @Test
    void createWithParentDirs_returnsSuccess(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("a/b/c/new.txt");

        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of("path", file.toString(), "content", "deep"));
        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        assertEquals("deep", Files.readString(file));
    }

    @Test
    void writeRelativePath_resolvesAgainstWorkDir(@TempDir Path tempDir) throws IOException {
        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of("path", "rel.txt", "content", "relative"));
        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        assertEquals("relative", Files.readString(tempDir.resolve("rel.txt")));
    }

    // --- Error cases ---

    @Test
    void missingPathParam_returnsError() {
        ToolContext ctx = allowAllContext("/tmp");
        ToolInput input = new ToolInput(Map.of("content", "data"));
        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Error.class, result);
        assertTrue(((ToolResult.Error) result).message().contains("Missing or empty"));
    }

    @Test
    void missingContentParam_returnsError() {
        ToolContext ctx = allowAllContext("/tmp");
        ToolInput input = new ToolInput(Map.of("path", "/tmp/test.txt"));
        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Error.class, result);
        assertTrue(((ToolResult.Error) result).message().contains("Missing required parameter: content"));
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
