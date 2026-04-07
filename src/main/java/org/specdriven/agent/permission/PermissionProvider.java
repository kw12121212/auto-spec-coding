package org.specdriven.agent.permission;

/**
 * Contract for checking, granting, and revoking permissions.
 */
public interface PermissionProvider {

    /**
     * Checks whether the given permission is allowed in the given context.
     */
    PermissionDecision check(Permission permission, PermissionContext context);

    /**
     * Grants the given permission in the given context.
     */
    void grant(Permission permission, PermissionContext context);

    /**
     * Revokes the given permission in the given context.
     */
    void revoke(Permission permission, PermissionContext context);
}
