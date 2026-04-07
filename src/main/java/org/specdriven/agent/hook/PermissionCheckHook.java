package org.specdriven.agent.hook;

import org.specdriven.agent.permission.Permission;
import org.specdriven.agent.permission.PermissionContext;
import org.specdriven.agent.permission.PermissionDecision;
import org.specdriven.agent.tool.Tool;
import org.specdriven.agent.tool.ToolContext;
import org.specdriven.agent.tool.ToolInput;
import org.specdriven.agent.tool.ToolResult;

/**
 * Hook that enforces permission checks before tool execution.
 * Uses the tool's {@link Tool#permissionFor(ToolInput)} to determine
 * the required permission, then delegates to the PermissionProvider.
 */
public class PermissionCheckHook implements ToolExecutionHook {

    @Override
    public ToolResult beforeExecute(Tool tool, ToolInput input, ToolContext context) {
        Permission permission = tool.permissionFor(input, context);
        PermissionContext permCtx = new PermissionContext(tool.getName(), "execute", "agent");
        PermissionDecision decision = context.permissionProvider().check(permission, permCtx);
        return switch (decision) {
            case ALLOW -> null;
            case DENY -> new ToolResult.Error("Permission denied for " + tool.getName());
            case CONFIRM -> new ToolResult.Error("Permission requires explicit confirmation for " + tool.getName());
        };
    }

    @Override
    public void afterExecute(Tool tool, ToolInput input, ToolResult result) {
        // no-op
    }
}
