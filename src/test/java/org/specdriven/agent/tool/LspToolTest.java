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
import org.specdriven.agent.testsupport.MockLspServerMain;
import org.specdriven.agent.testsupport.SubprocessTestCommand;

class LspToolTest {

    private final LspTool tool = new LspTool();

    @Test
    void getName_returnsLsp() {
        assertEquals("lsp", tool.getName());
    }

    @Test
    void getDescription_isNonEmpty() {
        assertFalse(tool.getDescription().isBlank());
    }

    @Test
    void getParameters_declaresRequiredAndOptional() {
        List<ToolParameter> params = tool.getParameters();
        assertEquals(6, params.size());
        assertEquals("operation", params.get(0).name());
        assertTrue(params.get(0).required());
        assertEquals("file", params.get(1).name());
        assertTrue(params.get(1).required());
        assertEquals("line", params.get(2).name());
        assertFalse(params.get(2).required());
        assertEquals("character", params.get(3).name());
        assertFalse(params.get(3).required());
        assertEquals("serverCommand", params.get(4).name());
        assertFalse(params.get(4).required());
        assertEquals("timeout", params.get(5).name());
        assertFalse(params.get(5).required());
    }

    @Test
    void missingOperation_returnsError(@TempDir Path tempDir) {
        ToolResult result = tool.execute(new ToolInput(Map.of("file", tempDir.toString())), stubContext(tempDir));
        assertInstanceOf(ToolResult.Error.class, result);
        assertTrue(((ToolResult.Error) result).message().contains("Missing or empty required parameter: operation"));
    }

    @Test
    void blankOperation_returnsError(@TempDir Path tempDir) {
        ToolResult result = tool.execute(
                new ToolInput(Map.of("operation", " ", "file", tempDir.toString())),
                stubContext(tempDir));
        assertInstanceOf(ToolResult.Error.class, result);
        assertTrue(((ToolResult.Error) result).message().contains("Missing or empty required parameter: operation"));
    }

    @Test
    void invalidOperation_returnsError(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("Test.java");
        Files.writeString(file, "class Test {}");
        ToolResult result = tool.execute(
                new ToolInput(Map.of("operation", "unknownOp", "file", file.toString())),
                stubContext(tempDir));
        assertInstanceOf(ToolResult.Error.class, result);
        assertTrue(((ToolResult.Error) result).message().contains("Invalid operation: unknownOp"));
    }

    @Test
    void missingFile_returnsError() {
        ToolResult result = tool.execute(new ToolInput(Map.of("operation", "hover")), stubContext(Path.of("/tmp")));
        assertInstanceOf(ToolResult.Error.class, result);
        assertTrue(((ToolResult.Error) result).message().contains("Missing or empty required parameter: file"));
    }

    @Test
    void nonexistentFile_returnsError(@TempDir Path tempDir) {
        ToolResult result = tool.execute(
                new ToolInput(Map.of("operation", "hover", "file", "/nonexistent/File.java")),
                stubContext(tempDir));
        assertInstanceOf(ToolResult.Error.class, result);
        assertTrue(((ToolResult.Error) result).message().contains("Failed to read file"));
    }

    @Test
    void noServerCommand_returnsError(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("Test.java");
        Files.writeString(file, "class Test {}");
        ToolResult result = tool.execute(
                new ToolInput(Map.of("operation", "diagnostics", "file", file.toString())),
                stubContext(tempDir));
        assertInstanceOf(ToolResult.Error.class, result);
        assertTrue(((ToolResult.Error) result).message().contains("No language server command"));
    }

    @Test
    void permissionFor_returnsExecuteLspPermission() {
        ToolInput input = new ToolInput(Map.of("operation", "diagnostics", "file", "/tmp/Test.java"));
        Permission perm = tool.permissionFor(input, stubContext(Path.of("/tmp")));
        assertEquals("execute", perm.action());
        assertEquals("lsp", perm.resource());
        assertEquals("diagnostics", perm.constraints().get("operation"));
        assertEquals("/tmp/Test.java", perm.constraints().get("file"));
    }

    @Test
    void diagnostics_withMockServer_returnsSuccess(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("Test.java");
        Files.writeString(file, "class Test {}");

        LspTool lspTool = new LspTool();
        try {
            ToolResult result = lspTool.execute(
                    new ToolInput(Map.of(
                            "operation", "diagnostics",
                            "file", file.toString(),
                            "serverCommand", command(),
                            "timeout", 10)),
                    stubContext(tempDir));

            assertInstanceOf(ToolResult.Success.class, result,
                    "Expected Success but got Error: " + (result instanceof ToolResult.Error e ? e.message() : ""));
            String output = ((ToolResult.Success) result).output();
            assertTrue(output.contains("Error"));
            assertTrue(output.contains("test error"));
        } finally {
            lspTool.close();
        }
    }

    @Test
    void hover_withMockServer_returnsSuccess(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("Test.java");
        Files.writeString(file, "class Test {}");

        LspTool lspTool = new LspTool();
        try {
            ToolResult result = lspTool.execute(
                    new ToolInput(Map.of(
                            "operation", "hover",
                            "file", file.toString(),
                            "line", 0,
                            "character", 6,
                            "serverCommand", command(),
                            "timeout", 10)),
                    stubContext(tempDir));

            assertInstanceOf(ToolResult.Success.class, result,
                    "Expected Success but got Error: " + (result instanceof ToolResult.Error e ? e.message() : ""));
            assertTrue(((ToolResult.Success) result).output().contains("test hover"));
        } finally {
            lspTool.close();
        }
    }

    @Test
    void goToDefinition_withMockServer_returnsSuccess(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("Test.java");
        Files.writeString(file, "class Test {}");

        LspTool lspTool = new LspTool();
        try {
            ToolResult result = lspTool.execute(
                    new ToolInput(Map.of(
                            "operation", "goToDefinition",
                            "file", file.toString(),
                            "line", 0,
                            "character", 6,
                            "serverCommand", command(),
                            "timeout", 10)),
                    stubContext(tempDir));

            assertInstanceOf(ToolResult.Success.class, result,
                    "Expected Success but got Error: " + (result instanceof ToolResult.Error e ? e.message() : ""));
            assertTrue(((ToolResult.Success) result).output().contains("test.java"));
        } finally {
            lspTool.close();
        }
    }

    @Test
    void references_withMockServer_returnsSuccess(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("Test.java");
        Files.writeString(file, "class Test {}");

        LspTool lspTool = new LspTool();
        try {
            ToolResult result = lspTool.execute(
                    new ToolInput(Map.of(
                            "operation", "references",
                            "file", file.toString(),
                            "line", 0,
                            "character", 6,
                            "serverCommand", command(),
                            "timeout", 10)),
                    stubContext(tempDir));

            assertInstanceOf(ToolResult.Success.class, result,
                    "Expected Success but got Error: " + (result instanceof ToolResult.Error e ? e.message() : ""));
            assertTrue(((ToolResult.Success) result).output().contains("test.java"));
        } finally {
            lspTool.close();
        }
    }

    @Test
    void documentSymbols_withMockServer_returnsSuccess(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("Test.java");
        Files.writeString(file, "class Test {}");

        LspTool lspTool = new LspTool();
        try {
            ToolResult result = lspTool.execute(
                    new ToolInput(Map.of(
                            "operation", "documentSymbols",
                            "file", file.toString(),
                            "serverCommand", command(),
                            "timeout", 10)),
                    stubContext(tempDir));

            assertInstanceOf(ToolResult.Success.class, result,
                    "Expected Success but got Error: " + (result instanceof ToolResult.Error e ? e.message() : ""));
            String output = ((ToolResult.Success) result).output();
            assertTrue(output.contains("TestClass"));
            assertTrue(output.contains("Class"));
        } finally {
            lspTool.close();
        }
    }

    @Test
    void hover_withoutLineAndChar_returnsError(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("Test.java");
        Files.writeString(file, "class Test {}");

        LspTool lspTool = new LspTool();
        try {
            ToolResult result = lspTool.execute(
                    new ToolInput(Map.of(
                            "operation", "hover",
                            "file", file.toString(),
                            "serverCommand", command(),
                            "timeout", 10)),
                    stubContext(tempDir));

            assertInstanceOf(ToolResult.Error.class, result);
            assertTrue(((ToolResult.Error) result).message().contains("line")
                    || ((ToolResult.Error) result).message().contains("character"));
        } finally {
            lspTool.close();
        }
    }

    private static ToolContext stubContext(Path workDir) {
        return new ToolContext() {
            @Override public String workDir() { return workDir.toString(); }
            @Override public org.specdriven.agent.permission.PermissionProvider permissionProvider() {
                return new org.specdriven.agent.permission.PermissionProvider() {
                    @Override public org.specdriven.agent.permission.PermissionDecision check(org.specdriven.agent.permission.Permission p, org.specdriven.agent.permission.PermissionContext c) { return org.specdriven.agent.permission.PermissionDecision.ALLOW; }
                    @Override public void grant(org.specdriven.agent.permission.Permission p, org.specdriven.agent.permission.PermissionContext c) {}
                    @Override public void revoke(org.specdriven.agent.permission.Permission p, org.specdriven.agent.permission.PermissionContext c) {}
                };
            }
            @Override public Map<String, String> env() { return Map.of(); }
        };
    }

    private static String command() {
        return SubprocessTestCommand.javaCommand(MockLspServerMain.class, "tool");
    }
}
