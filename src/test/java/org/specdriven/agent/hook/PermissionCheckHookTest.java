package org.specdriven.agent.hook;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.permission.Permission;
import org.specdriven.agent.permission.PermissionContext;
import org.specdriven.agent.permission.PermissionDecision;
import org.specdriven.agent.permission.PermissionProvider;
import org.specdriven.agent.tool.Tool;
import org.specdriven.agent.tool.ToolContext;
import org.specdriven.agent.tool.ToolInput;
import org.specdriven.agent.tool.ToolResult;

class PermissionCheckHookTest {

    private final PermissionCheckHook hook = new PermissionCheckHook();

    @Test
    void beforeExecute_allow_returnsNull() {
        PermissionProvider allowAll = new PermissionProvider() {
            @Override public PermissionDecision check(Permission p, PermissionContext c) { return PermissionDecision.ALLOW; }
            @Override public void grant(Permission p, PermissionContext c) {}
            @Override public void revoke(Permission p, PermissionContext c) {}
        };
        ToolContext ctx = contextWith(allowAll);
        StubTool tool = new StubTool();
        ToolInput input = new ToolInput(Map.of());

        assertNull(hook.beforeExecute(tool, input, ctx));
    }

    @Test
    void beforeExecute_deny_returnsError() {
        PermissionProvider denier = new PermissionProvider() {
            @Override public PermissionDecision check(Permission p, PermissionContext c) { return PermissionDecision.DENY; }
            @Override public void grant(Permission p, PermissionContext c) {}
            @Override public void revoke(Permission p, PermissionContext c) {}
        };
        ToolContext ctx = contextWith(denier);
        StubTool tool = new StubTool();
        ToolInput input = new ToolInput(Map.of());

        ToolResult result = hook.beforeExecute(tool, input, ctx);
        assertInstanceOf(ToolResult.Error.class, result);
        assertTrue(((ToolResult.Error) result).message().contains("Permission denied"));
    }

    @Test
    void beforeExecute_confirm_returnsError() {
        PermissionProvider confirmer = new PermissionProvider() {
            @Override public PermissionDecision check(Permission p, PermissionContext c) { return PermissionDecision.CONFIRM; }
            @Override public void grant(Permission p, PermissionContext c) {}
            @Override public void revoke(Permission p, PermissionContext c) {}
        };
        ToolContext ctx = contextWith(confirmer);
        StubTool tool = new StubTool();
        ToolInput input = new ToolInput(Map.of());

        ToolResult result = hook.beforeExecute(tool, input, ctx);
        assertInstanceOf(ToolResult.Error.class, result);
        assertTrue(((ToolResult.Error) result).message().contains("explicit confirmation"));
    }

    @Test
    void afterExecute_isNoOp() {
        // Should not throw
        hook.afterExecute(new StubTool(), new ToolInput(Map.of()), new ToolResult.Success("ok"));
    }

    @Test
    void beforeExecute_usesToolPermissionFor() {
        // Verify the hook calls permissionFor on the tool
        boolean[] called = {false};
        PermissionProvider allowAll = new PermissionProvider() {
            @Override public PermissionDecision check(Permission p, PermissionContext c) {
                called[0] = true;
                assertEquals("custom-action", p.action());
                assertEquals("custom-resource", p.resource());
                return PermissionDecision.ALLOW;
            }
            @Override public void grant(Permission p, PermissionContext c) {}
            @Override public void revoke(Permission p, PermissionContext c) {}
        };
        ToolContext ctx = contextWith(allowAll);
        ToolInput input = new ToolInput(Map.of("key", "value"));

        Tool tool = new Tool() {
            @Override public String getName() { return "custom"; }
            @Override public String getDescription() { return "test"; }
            @Override public java.util.List<org.specdriven.agent.tool.ToolParameter> getParameters() { return java.util.List.of(); }
            @Override public ToolResult execute(ToolInput i, ToolContext c) { return new ToolResult.Success(""); }
            @Override public Permission permissionFor(ToolInput i, ToolContext c) {
                return new Permission("custom-action", "custom-resource", Map.of("key", i.parameters().get("key").toString()));
            }
        };

        assertNull(hook.beforeExecute(tool, input, ctx));
        assertTrue(called[0]);
    }

    // --- Helpers ---

    private static ToolContext contextWith(PermissionProvider provider) {
        return new ToolContext() {
            @Override public String workDir() { return "/tmp"; }
            @Override public PermissionProvider permissionProvider() { return provider; }
            @Override public Map<String, String> env() { return Map.of(); }
        };
    }

    private static class StubTool implements Tool {
        @Override public String getName() { return "stub"; }
        @Override public String getDescription() { return "stub tool"; }
        @Override public java.util.List<org.specdriven.agent.tool.ToolParameter> getParameters() { return java.util.List.of(); }
        @Override public ToolResult execute(ToolInput input, ToolContext context) { return new ToolResult.Success("ok"); }
    }
}
