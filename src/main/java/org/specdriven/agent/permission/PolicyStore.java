package org.specdriven.agent.permission;

import java.util.List;
import java.util.Optional;

/**
 * Persistent store for permission policies and audit entries.
 */
public interface PolicyStore {

    /**
     * Persists an ALLOW decision for the given permission and context.
     */
    void grant(Permission permission, PermissionContext context);

    /**
     * Removes any stored decision for the given permission and context.
     */
    void revoke(Permission permission, PermissionContext context);

    /**
     * Looks up a stored decision for the given permission and context.
     *
     * @return the stored decision, or empty if no policy is stored
     */
    Optional<PermissionDecision> find(Permission permission, PermissionContext context);

    /**
     * Returns all active stored policies.
     */
    List<StoredPolicy> listPolicies();

    /**
     * Returns recent grant/revoke audit entries, ordered by timestamp descending.
     */
    List<AuditEntry> auditLog();
}
