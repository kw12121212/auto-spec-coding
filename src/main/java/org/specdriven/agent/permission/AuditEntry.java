package org.specdriven.agent.permission;

import java.util.Map;

/**
 * An entry in the permission audit log, recording a grant or revoke operation.
 *
 * @param id          unique identifier
 * @param operation   "GRANT" or "REVOKE"
 * @param action      the permission action
 * @param resource    the target resource
 * @param requester   the identity that requested the operation
 * @param performedBy the identity that performed the grant/revoke
 * @param timestamp   epoch millis when the operation occurred
 * @param metadata    additional metadata
 */
public record AuditEntry(
        String id,
        String operation,
        String action,
        String resource,
        String requester,
        String performedBy,
        long timestamp,
        Map<String, String> metadata
) {}
