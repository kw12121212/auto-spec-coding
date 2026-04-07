package org.specdriven.agent.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.permission.Permission;

class ToolPermissionForTest {

    private static ToolContext ctx(String workDir) {
        return new ToolContext() {
            @Override public String workDir() { return workDir; }
            @Override public org.specdriven.agent.permission.PermissionProvider permissionProvider() {
                return new org.specdriven.agent.permission.DefaultPermissionProvider(workDir);
            }
            @Override public Map<String, String> env() { return Map.of(); }
        };
    }

    @Test
    void bashTool_permissionFor_executeAction() {
        BashTool tool = new BashTool();
        ToolInput input = new ToolInput(Map.of("command", "ls -la"));
        Permission perm = tool.permissionFor(input, ctx("/tmp"));

        assertEquals("execute", perm.action());
        assertEquals("bash", perm.resource());
        assertEquals("ls -la", perm.constraints().get("command"));
    }

    @Test
    void readTool_permissionFor_readAction() {
        ReadTool tool = new ReadTool();
        ToolInput input = new ToolInput(Map.of("path", "src/Main.java"));
        Permission perm = tool.permissionFor(input, ctx("/project"));

        assertEquals("read", perm.action());
        assertTrue(perm.resource().endsWith("Main.java"));
    }

    @Test
    void writeTool_permissionFor_writeAction() {
        WriteTool tool = new WriteTool();
        ToolInput input = new ToolInput(Map.of("path", "out.txt", "content", "hello"));
        Permission perm = tool.permissionFor(input, ctx("/project"));

        assertEquals("write", perm.action());
        assertTrue(perm.resource().endsWith("out.txt"));
    }

    @Test
    void editTool_permissionFor_editAction() {
        EditTool tool = new EditTool();
        ToolInput input = new ToolInput(Map.of("path", "config.yaml", "old_string", "a", "new_string", "b"));
        Permission perm = tool.permissionFor(input, ctx("/project"));

        assertEquals("edit", perm.action());
        assertTrue(perm.resource().endsWith("config.yaml"));
    }

    @Test
    void grepTool_permissionFor_searchAction() {
        GrepTool tool = new GrepTool();
        ToolInput input = new ToolInput(Map.of("pattern", "TODO"));
        Permission perm = tool.permissionFor(input, ctx("/project"));

        assertEquals("search", perm.action());
    }

    @Test
    void globTool_permissionFor_searchAction() {
        GlobTool tool = new GlobTool();
        ToolInput input = new ToolInput(Map.of("pattern", "**/*.java"));
        Permission perm = tool.permissionFor(input, ctx("/project"));

        assertEquals("search", perm.action());
    }

    @Test
    void defaultPermissionFor_returnsExecuteWithToolName() {
        Tool customTool = new Tool() {
            @Override public String getName() { return "custom"; }
            @Override public String getDescription() { return ""; }
            @Override public java.util.List<ToolParameter> getParameters() { return java.util.List.of(); }
            @Override public ToolResult execute(ToolInput input, ToolContext context) { return new ToolResult.Success(""); }
        };

        Permission perm = customTool.permissionFor(new ToolInput(Map.of()), ctx("/tmp"));
        assertEquals("execute", perm.action());
        assertEquals("custom", perm.resource());
        assertTrue(perm.constraints().isEmpty());
    }
}
