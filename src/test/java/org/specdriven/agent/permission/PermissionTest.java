package org.specdriven.agent.permission;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

class PermissionTest {

    @Test
    void permissionDecisionDefinesAllowDenyConfirm() {
        assertEquals(PermissionDecision.ALLOW, PermissionDecision.valueOf("ALLOW"));
        assertEquals(PermissionDecision.DENY, PermissionDecision.valueOf("DENY"));
        assertEquals(PermissionDecision.CONFIRM, PermissionDecision.valueOf("CONFIRM"));
    }

    @Test
    void permissionConstruction() {
        Permission perm = new Permission("execute", "/bin/bash", Map.of("timeout", "30"));
        assertEquals("execute", perm.action());
        assertEquals("/bin/bash", perm.resource());
        assertEquals("30", perm.constraints().get("timeout"));
    }

    @Test
    void permissionContextConstruction() {
        PermissionContext ctx = new PermissionContext("bash-tool", "run", "agent-1");
        assertEquals("bash-tool", ctx.toolName());
        assertEquals("run", ctx.operation());
        assertEquals("agent-1", ctx.requester());
    }

    @Test
    void permissionEquality() {
        Permission a = new Permission("read", "/tmp/file", Map.of());
        Permission b = new Permission("read", "/tmp/file", Map.of());
        assertEquals(a, b);
    }

    @Test
    void defaultProviderAllowsReadInsideWorkDir() {
        DefaultPermissionProvider provider = new DefaultPermissionProvider("/workspace");

        PermissionDecision decision = provider.check(
                new Permission("read", "/workspace/src/Main.java", Map.of()),
                new PermissionContext("read", "read", "agent"));

        assertEquals(PermissionDecision.ALLOW, decision);
    }

    @Test
    void defaultProviderDeniesSearchOutsideWorkDir() {
        DefaultPermissionProvider provider = new DefaultPermissionProvider("/workspace");

        PermissionDecision decision = provider.check(
                new Permission("search", "/tmp", Map.of()),
                new PermissionContext("glob", "search", "agent"));

        assertEquals(PermissionDecision.DENY, decision);
    }

    @Test
    void defaultProviderRequiresConfirmationForExecutionAndMutation() {
        DefaultPermissionProvider provider = new DefaultPermissionProvider("/workspace");

        assertEquals(PermissionDecision.CONFIRM, provider.check(
                new Permission("execute", "bash", Map.of("command", "pwd")),
                new PermissionContext("bash", "execute", "agent")));
        assertEquals(PermissionDecision.CONFIRM, provider.check(
                new Permission("write", "/workspace/out.txt", Map.of()),
                new PermissionContext("write", "write", "agent")));
        assertEquals(PermissionDecision.CONFIRM, provider.check(
                new Permission("edit", "/workspace/out.txt", Map.of()),
                new PermissionContext("edit", "edit", "agent")));
    }
}
