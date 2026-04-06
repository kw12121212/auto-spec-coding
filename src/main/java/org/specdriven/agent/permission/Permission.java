package org.specdriven.agent.permission;

import java.util.Map;

/**
 * Describes a permission: what action on what resource, with optional constraints.
 *
 * @param action      the action being permitted (e.g. "execute", "read", "write")
 * @param resource    the target resource (e.g. "/bin/bash", "/tmp/workspace")
 * @param constraints additional constraints on the permission
 */
public record Permission(
        String action,
        String resource,
        Map<String, String> constraints
) {}
