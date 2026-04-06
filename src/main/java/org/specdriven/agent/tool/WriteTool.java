package org.specdriven.agent.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.specdriven.agent.permission.Permission;
import org.specdriven.agent.permission.PermissionContext;

/**
 * Tool that writes content to a file, creating parent directories as needed.
 */
public class WriteTool implements Tool {

    private static final String NAME = "write";

    private static final List<ToolParameter> PARAMETERS = List.of(
            new ToolParameter("path", "string", "Path to the file to write", true),
            new ToolParameter("content", "string", "Content to write to the file", true)
    );

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Writes content to a file, creating the file and parent directories if needed";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return PARAMETERS;
    }

    @Override
    public ToolResult execute(ToolInput input, ToolContext context) {
        // Validate path parameter
        Object pathObj = input.parameters().get("path");
        if (pathObj == null || pathObj.toString().isBlank()) {
            return new ToolResult.Error("Missing or empty required parameter: path");
        }

        // Validate content parameter
        Object contentObj = input.parameters().get("content");
        if (contentObj == null) {
            return new ToolResult.Error("Missing required parameter: content");
        }
        String content = contentObj.toString();

        // Resolve file path
        Path filePath = ReadTool.resolvePath(pathObj.toString(), context.workDir());

        // Permission check
        Permission permission = new Permission("write", filePath.toString(), Map.of());
        PermissionContext permCtx = new PermissionContext(NAME, "write", "agent");
        if (!context.permissionProvider().check(permission, permCtx)) {
            return new ToolResult.Error("Permission denied for write: " + filePath);
        }

        // Execute write
        try {
            Path parent = filePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.writeString(filePath, content);
            return new ToolResult.Success("Wrote " + content.length() + " characters to " + filePath);
        } catch (IOException e) {
            return new ToolResult.Error("Failed to write file: " + e.getMessage(), e);
        }
    }
}
