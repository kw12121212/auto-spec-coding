package org.specdriven.agent.tool;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.specdriven.agent.permission.Permission;

/**
 * Tool that reads file contents, optionally a line range.
 */
public class ReadTool implements Tool {

    private static final String NAME = "read";

    private static final List<ToolParameter> PARAMETERS = List.of(
            new ToolParameter("path", "string", "Path to the file to read", true),
            new ToolParameter("offset", "integer", "1-based starting line number (optional)", false),
            new ToolParameter("limit", "integer", "Maximum number of lines to return (optional)", false)
    );

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Reads file contents and returns them as a string, optionally a line range";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return PARAMETERS;
    }

    @Override
    public Permission permissionFor(ToolInput input, ToolContext context) {
        Object pathObj = input.parameters().get("path");
        String path = pathObj != null ? pathObj.toString() : "";
        Path filePath = resolvePath(path, context.workDir());
        return new Permission("read", filePath.toString(), Map.of());
    }

    @Override
    public ToolResult execute(ToolInput input, ToolContext context) {
        // Validate path parameter
        Object pathObj = input.parameters().get("path");
        if (pathObj == null || pathObj.toString().isBlank()) {
            return new ToolResult.Error("Missing or empty required parameter: path");
        }

        // Resolve file path
        Path filePath = resolvePath(pathObj.toString(), context.workDir());

        // Resolve optional offset/limit
        Integer offset = null;
        Integer limit = null;
        Object offsetObj = input.parameters().get("offset");
        if (offsetObj != null) {
            offset = ((Number) offsetObj).intValue();
        }
        Object limitObj = input.parameters().get("limit");
        if (limitObj != null) {
            limit = ((Number) limitObj).intValue();
        }

        // Execute read
        try {
            if (offset == null && limit == null) {
                return new ToolResult.Success(Files.readString(filePath));
            }

            // Line-range read
            try (BufferedReader reader = Files.newBufferedReader(filePath)) {
                List<String> lines = new ArrayList<>();
                String line;
                int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    if (offset != null && lineNumber < offset) {
                        continue;
                    }
                    lines.add(line);
                    if (limit != null && lines.size() >= limit) {
                        break;
                    }
                }
                return new ToolResult.Success(String.join("\n", lines));
            }
        } catch (IOException e) {
            return new ToolResult.Error("Failed to read file: " + e.getMessage(), e);
        }
    }

    static Path resolvePath(String raw, String workDir) {
        Path path = Path.of(raw);
        if (!path.isAbsolute()) {
            path = Path.of(workDir).resolve(path);
        }
        return path.normalize();
    }
}
