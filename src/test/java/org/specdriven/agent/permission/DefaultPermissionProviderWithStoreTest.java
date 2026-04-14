package org.specdriven.agent.permission;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DefaultPermissionProviderWithStoreTest {

    private DefaultPermissionProvider withStore;
    private DefaultPermissionProvider withoutStore;

    @BeforeEach
    void setUp() {
        String dbName = "test_perm_prov_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String jdbcUrl = "jdbc:lealone:embed:" + dbName + "?PERSISTENT=false";
        PolicyStore store = new LealonePolicyStore(jdbcUrl);

        withStore = new DefaultPermissionProvider("/tmp/workspace", store);
        withoutStore = new DefaultPermissionProvider("/tmp/workspace");
    }

    @Test
    void storedAllow_overridesDefaultConfirm() {
        // Default: execute -> CONFIRM
        Permission perm = new Permission("execute", "/bin/bash", Map.of());
        PermissionContext ctx = new PermissionContext("bash-tool", "run", "agent-1");

        assertEquals(PermissionDecision.CONFIRM, withoutStore.check(perm, ctx));

        withStore.grant(perm, ctx);
        assertEquals(PermissionDecision.ALLOW, withStore.check(perm, ctx));
    }

    @Test
    void storedAllow_overridesDefaultDenyForOutsideWorkDir() {
        // Default: read outside workDir -> DENY
        Permission perm = new Permission("read", "/etc/shadow", Map.of());
        PermissionContext ctx = new PermissionContext("read-tool", "read", "agent-1");

        assertEquals(PermissionDecision.DENY, withoutStore.check(perm, ctx));

        withStore.grant(perm, ctx);
        assertEquals(PermissionDecision.ALLOW, withStore.check(perm, ctx));
    }

    @Test
    void noStore_grantAndRevokeAreNoOps() {
        Permission perm = new Permission("execute", "/bin/bash", Map.of());
        PermissionContext ctx = new PermissionContext("bash-tool", "run", "agent-1");

        withoutStore.grant(perm, ctx);
        // Still CONFIRM (no store, so grant is a no-op)
        assertEquals(PermissionDecision.CONFIRM, withoutStore.check(perm, ctx));

        withoutStore.revoke(perm, ctx);
        assertEquals(PermissionDecision.CONFIRM, withoutStore.check(perm, ctx));
    }

    @Test
    void withStore_fallsThroughWhenNoStoredPolicy() {
        // No policy stored — should fall through to default
        Permission perm = new Permission("execute", "/bin/bash", Map.of());
        PermissionContext ctx = new PermissionContext("bash-tool", "run", "agent-1");

        assertEquals(PermissionDecision.CONFIRM, withStore.check(perm, ctx));
    }

    @Test
    void withStore_revokeThenCheck_fallsThroughToDefault() {
        Permission perm = new Permission("execute", "/bin/bash", Map.of());
        PermissionContext ctx = new PermissionContext("bash-tool", "run", "agent-1");

        withStore.grant(perm, ctx);
        assertEquals(PermissionDecision.ALLOW, withStore.check(perm, ctx));

        withStore.revoke(perm, ctx);
        assertEquals(PermissionDecision.CONFIRM, withStore.check(perm, ctx));
    }

    @Test
    void defaultPolicyDeniesHotLoadActionWithoutStoredGrant() {
        Permission perm = new Permission("skill.hotload.load", "skill:demo", Map.of());
        PermissionContext ctx = new PermissionContext("skill-hot-loader", "load", "agent-1");

        assertEquals(PermissionDecision.DENY, withStore.check(perm, ctx));
        assertEquals(PermissionDecision.DENY, withoutStore.check(perm, ctx));
    }

    @Test
    void storedPolicyCanAllowHotLoadAction() {
        Permission perm = new Permission("skill.hotload.replace", "skill:demo", Map.of());
        PermissionContext ctx = new PermissionContext("skill-hot-loader", "replace", "agent-1");

        withStore.grant(perm, ctx);

        assertEquals(PermissionDecision.ALLOW, withStore.check(perm, ctx));
    }

    @Test
    void defaultPermissionProvider_deniesLlmConfigSet() {
        Permission perm = new Permission("llm.config.set", "session:session-a", Map.of());
        PermissionContext ctx = new PermissionContext("llm-runtime-config", "set", "agent-1");

        assertEquals(PermissionDecision.DENY, withStore.check(perm, ctx));
        assertEquals(PermissionDecision.DENY, withoutStore.check(perm, ctx));
    }

    @Test
    void defaultPermissionProvider_allowsLlmConfigSet_withStoredPolicy() {
        Permission perm = new Permission("llm.config.set", "session:session-a", Map.of());
        PermissionContext ctx = new PermissionContext("llm-runtime-config", "set", "agent-1");

        withStore.grant(perm, ctx);

        assertEquals(PermissionDecision.ALLOW, withStore.check(perm, ctx));
    }
}
