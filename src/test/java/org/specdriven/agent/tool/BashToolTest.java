package org.specdriven.agent.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.specdriven.agent.permission.Permission;
import org.specdriven.agent.permission.PermissionContext;
import org.specdriven.agent.permission.PermissionDecision;
import org.specdriven.agent.permission.PermissionProvider;

class BashToolTest {

    private final BashTool tool = new BashTool();

    // --- Identity tests ---

    @Test
    void getName_returnsBash() {
        assertEquals("bash", tool.getName());
    }

    @Test
    void getDescription_isNonEmpty() {
        assertFalse(tool.getDescription().isBlank());
    }

    @Test
    void getParameters_declaresCommandTimeoutWorkDir() {
        List<ToolParameter> params = tool.getParameters();
        assertEquals(4, params.size());
        assertEquals("command", params.get(0).name());
        assertTrue(params.get(0).required());
        assertEquals("timeout", params.get(1).name());
        assertFalse(params.get(1).required());
        assertEquals("workDir", params.get(2).name());
        assertFalse(params.get(2).required());
        assertEquals("profile", params.get(3).name());
        assertFalse(params.get(3).required());
    }

    @Test
    void explicitProfileUsesProfileBoundExecutor() {
        BashTool profiledTool = new BashTool((projectRoot, executionConfig, requestedProfile, command) ->
                java.util.Optional.of(new ProfileBoundCommandExecutor.ExecutionResult(
                        requestedProfile,
                        command,
                        0,
                        "profile:" + requestedProfile,
                        "")));

        ToolResult result = profiledTool.execute(new ToolInput(Map.of(
                "command", "echo hello",
                "profile", "ci"
        )), allowAllContext("/tmp"));

        assertInstanceOf(ToolResult.Success.class, result);
        assertEquals("profile:ci", ((ToolResult.Success) result).output());
    }

    @Test
    void requestedProfileFailureDoesNotFallBackToHostExecution() {
        BashTool profiledTool = new BashTool((projectRoot, executionConfig, requestedProfile, command) -> {
            throw new IllegalStateException("Unknown requested environment profile 'prod'");
        });

        ToolResult result = profiledTool.execute(new ToolInput(Map.of(
                "command", "echo hello",
                "profile", "prod"
        )), allowAllContext("/tmp"));

        assertInstanceOf(ToolResult.Error.class, result);
        assertTrue(((ToolResult.Error) result).message().contains("Unknown requested environment profile 'prod'"));
    }

    @Test
    void profileExecutorFallsBackToHostExecutionWhenNoProfileResolves() {
        BashTool profiledTool = new BashTool((projectRoot, executionConfig, requestedProfile, command) ->
                java.util.Optional.empty());

        ToolResult result = profiledTool.execute(new ToolInput(Map.of("command", "echo hello")), allowAllContext("/tmp"));

        assertInstanceOf(ToolResult.Success.class, result);
        assertEquals("hello", ((ToolResult.Success) result).output());
    }

    @Test
    void deniedProfiledExecutionIsRejectedBeforeLaunch() {
        BashTool profiledTool = new BashTool((projectRoot, executionConfig, requestedProfile, command) -> {
            fail("profile executor should not run when permission is denied");
            return java.util.Optional.empty();
        });

        ToolResult result = profiledTool.execute(new ToolInput(Map.of(
                "command", "echo hello",
                "profile", "ci"
        )), contextWithDecision("/tmp", PermissionDecision.DENY));

        assertInstanceOf(ToolResult.Error.class, result);
        assertTrue(((ToolResult.Error) result).message().contains("Permission denied for bash"));
    }

    @Test
    void confirmationRequiredProfiledExecutionIsRejectedBeforeLaunch() {
        BashTool profiledTool = new BashTool((projectRoot, executionConfig, requestedProfile, command) -> {
            fail("profile executor should not run when confirmation is required");
            return java.util.Optional.empty();
        });

        ToolResult result = profiledTool.execute(new ToolInput(Map.of(
                "command", "echo hello",
                "profile", "ci"
        )), contextWithDecision("/tmp", PermissionDecision.CONFIRM));

        assertInstanceOf(ToolResult.Error.class, result);
        assertTrue(((ToolResult.Error) result).message().contains("explicit confirmation"));
    }

    // --- Happy path ---

    @Test
    void echo_returnsSuccess() {
        ToolContext ctx = allowAllContext("/tmp");
        ToolInput input = new ToolInput(Map.of("command", "echo hello"));
        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        assertEquals("hello", ((ToolResult.Success) result).output());
    }

    // --- Working directory ---

    @Test
    void customWorkDir_isUsed(@TempDir Path tempDir) {
        ToolContext ctx = allowAllContext("/tmp");
        ToolInput input = new ToolInput(Map.of(
                "command", "pwd",
                "workDir", tempDir.toString()
        ));
        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Success.class, result);
        assertEquals(tempDir.toString(), ((ToolResult.Success) result).output().trim());
    }

    // --- Timeout ---

    @Test
    void timeout_returnsError() {
        ToolContext ctx = allowAllContext("/tmp");
        ToolInput input = new ToolInput(Map.of(
                "command", "sleep 10",
                "timeout", 1
        ));
        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Error.class, result);
        assertTrue(((ToolResult.Error) result).message().contains("timed out"));
    }

    // --- Missing command ---

    @Test
    void missingCommand_returnsError() {
        ToolContext ctx = allowAllContext("/tmp");
        ToolResult result = tool.execute(ToolInput.empty(), ctx);

        assertInstanceOf(ToolResult.Error.class, result);
        assertTrue(((ToolResult.Error) result).message().contains("Missing or empty"));
    }

    // --- Non-zero exit code ---

    @Test
    void nonZeroExitCode_returnsError() {
        ToolContext ctx = allowAllContext("/tmp");
        ToolInput input = new ToolInput(Map.of("command", "exit 42"));
        ToolResult result = tool.execute(input, ctx);

        assertInstanceOf(ToolResult.Error.class, result);
        assertTrue(((ToolResult.Error) result).message().contains("exited with code 42"));
    }

    // --- Helpers ---

    private static ToolContext allowAllContext(String workDir) {
        return contextWithDecision(workDir, PermissionDecision.ALLOW);
    }

    private static ToolContext contextWithDecision(String workDir, PermissionDecision decision) {
        PermissionProvider allowAll = new PermissionProvider() {
            @Override public PermissionDecision check(Permission p, PermissionContext c) { return decision; }
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
