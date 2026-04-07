package org.specdriven.agent.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.specdriven.agent.permission.Permission;

/**
 * Tool that finds files matching glob patterns across directory trees.
 */
public class GlobTool implements Tool {

    private static final String NAME = "glob";

    private static final List<ToolParameter> PARAMETERS = List.of(
            new ToolParameter("pattern", "string", "Glob pattern to match files against (e.g. \"**/*.java\")", true),
            new ToolParameter("path", "string", "Root directory to search in (default: context workDir)", false),
            new ToolParameter("head_limit", "integer", "Maximum number of results to return", false)
    );

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Finds files matching glob patterns across directory trees, sorted by modification time";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return PARAMETERS;
    }

    @Override
    public Permission permissionFor(ToolInput input, ToolContext context) {
        String pathStr = stringParam(input, "path", context.workDir());
        Path searchRoot = resolvePath(pathStr, context.workDir());
        return new Permission("search", searchRoot.toString(), Map.of());
    }

    @Override
    public ToolResult execute(ToolInput input, ToolContext context) {
        // Validate pattern
        Object patternObj = input.parameters().get("pattern");
        if (patternObj == null || patternObj.toString().isBlank()) {
            return new ToolResult.Error("Missing or empty required parameter: pattern");
        }
        String patternStr = patternObj.toString();

        // Compile glob pattern
        PathMatcher matcher;
        try {
            matcher = Path.of("").getFileSystem().getPathMatcher("glob:" + patternStr);
        } catch (Exception e) {
            return new ToolResult.Error("Invalid glob pattern: " + e.getMessage());
        }

        // Resolve search root
        String pathStr = stringParam(input, "path", context.workDir());
        Path searchRoot = resolvePath(pathStr, context.workDir());
        if (!Files.exists(searchRoot) || !Files.isDirectory(searchRoot)) {
            return new ToolResult.Error("Search path does not exist or is not a directory: " + searchRoot);
        }

        Integer headLimit = intParam(input, "head_limit");

        // Execute search
        try {
            String result = search(searchRoot, matcher, headLimit);
            return new ToolResult.Success(result);
        } catch (IOException e) {
            return new ToolResult.Error("Search failed: " + e.getMessage(), e);
        }
    }

    private String search(Path root, PathMatcher matcher, Integer headLimit) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            Stream<Path> matched = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> !Files.isSymbolicLink(p))
                    .filter(p -> matcher.matches(root.relativize(p)));

            // Sort by modification time descending
            matched = matched.sorted(Comparator
                    .<Path, FileTime>comparing(p -> {
                        try {
                            return Files.getLastModifiedTime(p);
                        } catch (IOException e) {
                            return FileTime.fromMillis(0);
                        }
                    }).reversed());

            if (headLimit != null) {
                matched = matched.limit(headLimit);
            }

            List<Path> results = matched.toList();
            if (results.isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            for (Path p : results) {
                sb.append(p.toAbsolutePath()).append('\n');
            }
            sb.setLength(sb.length() - 1);
            return sb.toString();
        }
    }

    static Path resolvePath(String raw, String workDir) {
        Path path = Path.of(raw);
        if (!path.isAbsolute()) {
            path = Path.of(workDir).resolve(path);
        }
        return path.normalize();
    }

    private static String stringParam(ToolInput input, String name, String defaultValue) {
        Object value = input.parameters().get(name);
        return value != null ? value.toString() : defaultValue;
    }

    private static Integer intParam(ToolInput input, String name) {
        Object value = input.parameters().get(name);
        return value != null ? ((Number) value).intValue() : null;
    }
}
