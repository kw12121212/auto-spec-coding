package org.specdriven.agent.permission;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

class PermissionTest {

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
}
