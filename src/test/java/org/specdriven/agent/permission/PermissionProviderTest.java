package org.specdriven.agent.permission;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class PermissionProviderTest {

    @Test
    void anonymousProviderSupportsCheckGrantRevoke() {
        Set<String> granted = new HashSet<>();
        PermissionProvider provider = new PermissionProvider() {
            @Override
            public boolean check(Permission permission, PermissionContext context) {
                return granted.contains(key(permission, context));
            }

            @Override
            public void grant(Permission permission, PermissionContext context) {
                granted.add(key(permission, context));
            }

            @Override
            public void revoke(Permission permission, PermissionContext context) {
                granted.remove(key(permission, context));
            }

            private String key(Permission p, PermissionContext c) {
                return c.requester() + ":" + p.action() + ":" + p.resource();
            }
        };

        Permission perm = new Permission("execute", "/bin/bash", Map.of());
        PermissionContext ctx = new PermissionContext("bash-tool", "run", "agent-1");

        assertFalse(provider.check(perm, ctx));
        provider.grant(perm, ctx);
        assertTrue(provider.check(perm, ctx));
        provider.revoke(perm, ctx);
        assertFalse(provider.check(perm, ctx));
    }
}
