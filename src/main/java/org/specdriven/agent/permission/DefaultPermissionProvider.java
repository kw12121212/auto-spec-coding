package org.specdriven.agent.permission;

import java.nio.file.Path;

/**
 * Default permission policy for the built-in tool surface.
 */
public class DefaultPermissionProvider implements PermissionProvider {

    private final Path workDir;

    public DefaultPermissionProvider(String workDir) {
        this.workDir = Path.of(workDir).toAbsolutePath().normalize();
    }

    @Override
    public PermissionDecision check(Permission permission, PermissionContext context) {
        return switch (permission.action()) {
            case "execute" -> PermissionDecision.CONFIRM;
            case "write", "edit" -> PermissionDecision.CONFIRM;
            case "read", "search" -> isWithinWorkDir(permission.resource())
                    ? PermissionDecision.ALLOW
                    : PermissionDecision.DENY;
            default -> PermissionDecision.DENY;
        };
    }

    @Override
    public void grant(Permission permission, PermissionContext context) {
        // Default policy is stateless; runtime grant/revoke behavior belongs to a later change.
    }

    @Override
    public void revoke(Permission permission, PermissionContext context) {
        // Default policy is stateless; runtime grant/revoke behavior belongs to a later change.
    }

    private boolean isWithinWorkDir(String resource) {
        try {
            Path resourcePath = Path.of(resource).toAbsolutePath().normalize();
            return resourcePath.startsWith(workDir);
        } catch (Exception e) {
            return false;
        }
    }
}
