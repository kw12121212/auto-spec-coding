package org.specdriven.agent.permission;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LealonePolicyStoreTest {

    private LealonePolicyStore store;
    private String jdbcUrl;

    @BeforeEach
    void setUp() {
        String dbName = "test_perm_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        jdbcUrl = "jdbc:lealone:embed:" + dbName + "?PERSISTENT=false";
        store = new LealonePolicyStore(jdbcUrl);
    }

    @Test
    void grant_storesAllowDecision() {
        Permission perm = new Permission("execute", "/bin/bash", Map.of());
        PermissionContext ctx = new PermissionContext("bash-tool", "run", "agent-1");

        store.grant(perm, ctx);

        Optional<PermissionDecision> found = store.find(perm, ctx);
        assertTrue(found.isPresent());
        assertEquals(PermissionDecision.ALLOW, found.get());
    }

    @Test
    void revoke_removesStoredDecision() {
        Permission perm = new Permission("execute", "/bin/bash", Map.of());
        PermissionContext ctx = new PermissionContext("bash-tool", "run", "agent-1");

        store.grant(perm, ctx);
        assertTrue(store.find(perm, ctx).isPresent());

        store.revoke(perm, ctx);
        assertTrue(store.find(perm, ctx).isEmpty());
    }

    @Test
    void find_returnsEmptyWhenNoPolicyStored() {
        Permission perm = new Permission("execute", "/bin/bash", Map.of());
        PermissionContext ctx = new PermissionContext("bash-tool", "run", "agent-1");

        Optional<PermissionDecision> found = store.find(perm, ctx);
        assertTrue(found.isEmpty());
    }

    @Test
    void grant_recordsAuditEntry() {
        Permission perm = new Permission("execute", "/bin/bash", Map.of());
        PermissionContext ctx = new PermissionContext("bash-tool", "run", "agent-1");

        store.grant(perm, ctx);

        List<AuditEntry> log = store.auditLog();
        assertEquals(1, log.size());
        AuditEntry entry = log.get(0);
        assertEquals("GRANT", entry.operation());
        assertEquals("execute", entry.action());
        assertEquals("/bin/bash", entry.resource());
        assertEquals("agent-1", entry.requester());
    }

    @Test
    void revoke_recordsAuditEntry() {
        Permission perm = new Permission("execute", "/bin/bash", Map.of());
        PermissionContext ctx = new PermissionContext("bash-tool", "run", "agent-1");

        store.grant(perm, ctx);
        store.revoke(perm, ctx);

        List<AuditEntry> log = store.auditLog();
        assertEquals(2, log.size());
        // Most recent first (REVOKE)
        assertEquals("REVOKE", log.get(0).operation());
        assertEquals("GRANT", log.get(1).operation());
    }

    @Test
    void listPolicies_returnsAllStoredPolicies() {
        Permission perm1 = new Permission("execute", "/bin/bash", Map.of());
        Permission perm2 = new Permission("read", "/etc/hosts", Map.of());
        PermissionContext ctx = new PermissionContext("tool", "run", "agent-1");

        store.grant(perm1, ctx);
        store.grant(perm2, ctx);

        List<StoredPolicy> policies = store.listPolicies();
        assertEquals(2, policies.size());
    }

    @Test
    void grant_upsertsExistingPolicy() {
        Permission perm = new Permission("execute", "/bin/bash", Map.of());
        PermissionContext ctx = new PermissionContext("bash-tool", "run", "agent-1");

        store.grant(perm, ctx);
        store.grant(perm, ctx);

        List<StoredPolicy> policies = store.listPolicies();
        assertEquals(1, policies.size());
    }

    @Test
    void find_matchesByActionResourceRequester() {
        Permission perm = new Permission("execute", "/bin/bash", Map.of());
        PermissionContext ctx1 = new PermissionContext("bash-tool", "run", "agent-1");
        PermissionContext ctx2 = new PermissionContext("bash-tool", "run", "agent-2");

        store.grant(perm, ctx1);

        assertTrue(store.find(perm, ctx1).isPresent());
        assertTrue(store.find(perm, ctx2).isEmpty());
    }
}
