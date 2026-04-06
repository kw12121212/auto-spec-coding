package org.specdriven.agent.permission;

/**
 * Context in which a permission check occurs.
 *
 * @param toolName  the tool requesting the operation
 * @param operation the specific operation being performed
 * @param requester the identity requesting the operation
 */
public record PermissionContext(
        String toolName,
        String operation,
        String requester
) {}
