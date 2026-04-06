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
import org.specdriven.agent.permission.PermissionProvider;

class EditToolTest {

    private final EditTool tool = new EditTool();

    // --- Identity tests ---

    @Test
    void getName_returnsEdit() {
        assertEquals("edit", tool.getName());
    }

    @Test
    void getDescription_isNonEmpty() {
        assertFalse(tool.getDescription().isBlank());
    }

    @Test
    void getParameters_declaresPathOldNew() {
        List<ToolParameter> params = tool.getParameters();
        assertEquals(3, params.size());
        assertEquals("path", params.get(0).name());
        assertTrue(params.get(0).required());
        assertEquals("old_string", params.get(1).name());
        assertTrue(params.get(1).required());
        assertEquals("new_string", params.get(2).name());
        assertTrue(params.get(2).required());
    }

    // --- Happy path ---

    @Test
    void successfulReplacement_returnsSuccess(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("edit.txt");
        Files.writeString(file, "hello world");

        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of(
                "path", file.toString(),
                "old_string", "world",
                "new_string", "there"
        ));
        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        assertEquals("hello there", Files.readString(file));
    }

    @Test
    void replacesOnlyFirstOccurrence(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("multi.txt");
        Files.writeString(file, "aaa bbb aaa");

        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of(
                "path", file.toString(),
                "old_string", "aaa",
                "new_string", "ccc"
        ));
        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        assertEquals("ccc bbb aaa", Files.readString(file));
    }

    @Test
    void editRelativePath_resolvesAgainstWorkDir(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("rel.txt");
        Files.writeString(file, "old text");

        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of(
                "path", "rel.txt",
                "old_string", "old",
                "new_string", "new"
        ));
        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        assertEquals("new text", Files.readString(file));
    }

    // --- Error cases ---

    @Test
    void oldStringNotFound_returnsError(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("edit.txt");
        Files.writeString(file, "hello world");

        ToolContext ctx = allowAllContext(tempDir.toString());
        ToolInput input = new ToolInput(Map.of(
                "path", file.toString(),
                "old_string", "not here",
                "new_string", "replacement"
        ));
        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Error.class, result);
        assertTrue(((ToolResult.Error) result).message().contains("old_string not found"));
    }

    @Test
    void missingPathParam_returnsError() {
        ToolContext ctx = allowAllContext("/tmp");
        ToolInput input = new ToolInput(Map.of("old_string", "a", "new_string", "b"));
        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Error.class, result);
        assertTrue(((ToolResult.Error) result).message().contains("Missing or empty"));
    }

    @Test
    void missingOldStringParam_returnsError() {
        ToolContext ctx = allowAllContext("/tmp");
        ToolInput input = new ToolInput(Map.of("path", "/tmp/test.txt", "new_string", "b"));
        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Error.class, result);
        assertTrue(((ToolResult.Error) result).message().contains("Missing required parameter: old_string"));
    }

    @Test
    void missingNewStringParam_returnsError() {
        ToolContext ctx = allowAllContext("/tmp");
        ToolInput input = new ToolInput(Map.of("path", "/tmp/test.txt", "old_string", "a"));
        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Error.class, result);
        assertTrue(((ToolResult.Error) result).message().contains("Missing required parameter: new_string"));
    }

    // --- Permission denied ---

    @Test
    void permissionDenied_returnsErrorWithoutEditing(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("protected.txt");
        Files.writeString(file, "original");

        PermissionProvider denier = new PermissionProvider() {
            @Override public boolean check(Permission p, PermissionContext c) { return false; }
            @Override public void grant(Permission p, PermissionContext c) {}
            @Override public void revoke(Permission p, PermissionContext c) {}
        };
        ToolContext ctx = new ToolContext() {
            @Override public String workDir() { return tempDir.toString(); }
            @Override public PermissionProvider permissionProvider() { return denier; }
            @Override public Map<String, String> env() { return Map.of(); }
        };

        ToolInput input = new ToolInput(Map.of(
                "path", file.toString(),
                "old_string", "original",
                "new_string", "changed"
        ));
        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Error.class, result);
        assertTrue(((ToolResult.Error) result).message().contains("Permission denied"));
        assertEquals("original", Files.readString(file));
    }

    // --- Helpers ---

    private static ToolContext allowAllContext(String workDir) {
        PermissionProvider allowAll = new PermissionProvider() {
            @Override public boolean check(Permission p, PermissionContext c) { return true; }
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
