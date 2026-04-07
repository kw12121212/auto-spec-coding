package org.specdriven.agent.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.specdriven.agent.permission.Permission;

/**
 * Tool that performs exact string replacement in an existing file.
 */
public class EditTool implements Tool {

    private static final String NAME = "edit";

    private static final List<ToolParameter> PARAMETERS = List.of(
            new ToolParameter("path", "string", "Path to the file to edit", true),
            new ToolParameter("old_string", "string", "Exact text to find and replace", true),
            new ToolParameter("new_string", "string", "Replacement text", true)
    );

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Performs exact string replacement in an existing file";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return PARAMETERS;
    }

    @Override
    public Permission permissionFor(ToolInput input, ToolContext context) {
        Object pathObj = input.parameters().get("path");
        String path = pathObj != null ? pathObj.toString() : "";
        Path filePath = ReadTool.resolvePath(path, context.workDir());
        return new Permission("edit", filePath.toString(), Map.of());
    }

    @Override
    public ToolResult execute(ToolInput input, ToolContext context) {
        // Validate path parameter
        Object pathObj = input.parameters().get("path");
        if (pathObj == null || pathObj.toString().isBlank()) {
            return new ToolResult.Error("Missing or empty required parameter: path");
        }

        // Validate old_string parameter
        Object oldStringObj = input.parameters().get("old_string");
        if (oldStringObj == null) {
            return new ToolResult.Error("Missing required parameter: old_string");
        }
        String oldString = oldStringObj.toString();

        // Validate new_string parameter
        Object newStringObj = input.parameters().get("new_string");
        if (newStringObj == null) {
            return new ToolResult.Error("Missing required parameter: new_string");
        }
        String newString = newStringObj.toString();

        // Resolve file path
        Path filePath = ReadTool.resolvePath(pathObj.toString(), context.workDir());

        // Execute edit
        try {
            String content = Files.readString(filePath);
            int index = content.indexOf(oldString);
            if (index < 0) {
                return new ToolResult.Error("old_string not found in file: " + filePath);
            }
            String updated = content.substring(0, index) + newString + content.substring(index + oldString.length());
            Files.writeString(filePath, updated);
            return new ToolResult.Success("Replaced text in " + filePath);
        } catch (IOException e) {
            return new ToolResult.Error("Failed to edit file: " + e.getMessage(), e);
        }
    }
}
