package org.specdriven.agent.permission;

/**
 * A persisted permission policy entry.
 *
 * @param id        unique identifier
 * @param permission the permission being stored
 * @param decision  the stored decision
 * @param createdAt creation timestamp (epoch millis)
 * @param updatedAt last update timestamp (epoch millis)
 */
public record StoredPolicy(
        String id,
        Permission permission,
        PermissionDecision decision,
        long createdAt,
        long updatedAt
) {}
