package org.specdriven.agent.permission;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Default permission policy for the built-in tool surface.
 * When a {@link PolicyStore} is provided, stored policies take precedence
 * over the hardcoded defaults.
 */
public class DefaultPermissionProvider implements PermissionProvider {

    private final Path workDir;
    private final PolicyStore store;

    public DefaultPermissionProvider(String workDir) {
        this(workDir, null);
    }

    public DefaultPermissionProvider(String workDir, PolicyStore store) {
        this.workDir = Path.of(workDir).toAbsolutePath().normalize();
        this.store = store;
    }

    @Override
    public PermissionDecision check(Permission permission, PermissionContext context) {
        if (store != null) {
            Optional<PermissionDecision> stored = store.find(permission, context);
            if (stored.isPresent()) {
                return stored.get();
            }
        }
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
        if (store != null) {
            store.grant(permission, context);
        }
    }

    @Override
    public void revoke(Permission permission, PermissionContext context) {
        if (store != null) {
            store.revoke(permission, context);
        }
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
