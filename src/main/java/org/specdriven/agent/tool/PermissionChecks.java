package org.specdriven.agent.tool;

import org.specdriven.agent.permission.Permission;
import org.specdriven.agent.permission.PermissionContext;
import org.specdriven.agent.permission.PermissionDecision;

final class PermissionChecks {

    private PermissionChecks() {
    }

    static ToolResult check(ToolContext context, Permission permission, PermissionContext permissionContext, String targetDescription) {
        PermissionDecision decision = context.permissionProvider().check(permission, permissionContext);
        return switch (decision) {
            case ALLOW -> null;
            case DENY -> new ToolResult.Error("Permission denied for " + targetDescription);
            case CONFIRM -> new ToolResult.Error("Permission requires explicit confirmation for " + targetDescription);
        };
    }
}
