package org.specdriven.agent.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.specdriven.agent.permission.Permission;
import org.specdriven.agent.tool.builtin.BuiltinTool;
import org.specdriven.agent.tool.builtin.BuiltinToolManager;

/**
 * Tool that finds files matching glob patterns across directory trees.
 * Uses fd binary via BuiltinToolManager when available for faster search,
 * falling back to pure Java Files.walk otherwise.
 */
public class GlobTool implements Tool {

    private static final String NAME = "glob";

    private static final List<ToolParameter> PARAMETERS = List.of(
            new ToolParameter("pattern", "string", "Glob pattern to match files against (e.g. \"**/*.java\")", true),
            new ToolParameter("path", "string", "Root directory to search in (default: context workDir)", false),
            new ToolParameter("head_limit", "integer", "Maximum number of results to return", false)
    );

    private final BuiltinToolManager builtinToolManager;

    public GlobTool() {
        this(null);
    }

    public GlobTool(BuiltinToolManager builtinToolManager) {
        this.builtinToolManager = builtinToolManager;
    }

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

        // Resolve search root
        String pathStr = stringParam(input, "path", context.workDir());
        Path searchRoot = resolvePath(pathStr, context.workDir());
        if (!Files.exists(searchRoot) || !Files.isDirectory(searchRoot)) {
            return new ToolResult.Error("Search path does not exist or is not a directory: " + searchRoot);
        }

        Integer headLimit = intParam(input, "head_limit");

        // Try fd first, fall back to pure Java
        String fdResult = searchWithFd(patternStr, searchRoot, headLimit);
        if (fdResult != null) {
            return new ToolResult.Success(fdResult);
        }

        // Fallback: pure Java traversal
        PathMatcher matcher;
        try {
            matcher = Path.of("").getFileSystem().getPathMatcher("glob:" + patternStr);
        } catch (Exception e) {
            return new ToolResult.Error("Invalid glob pattern: " + e.getMessage());
        }

        try {
            String result = search(searchRoot, matcher, headLimit);
            return new ToolResult.Success(result);
        } catch (IOException e) {
            return new ToolResult.Error("Search failed: " + e.getMessage(), e);
        }
    }

    /**
     * Attempts file search using fd binary. Returns null if fd is unavailable or fails,
     * triggering silent fallback to pure Java.
     */
    String searchWithFd(String pattern, Path searchRoot, Integer headLimit) {
        if (builtinToolManager == null) {
            return null;
        }

        Optional<Path> fdBinary = builtinToolManager.detect(BuiltinTool.FD);
        if (fdBinary.isEmpty()) {
            return null;
        }

        List<String> command = new ArrayList<>();
        command.add(fdBinary.get().toString());
        command.add("--glob");
        command.add(pattern);
        command.add("--absolute-path");
        if (headLimit != null) {
            command.add("--max-results");
            command.add(headLimit.toString());
        }
        command.add(searchRoot.toString());

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return null;
            }

            List<Path> paths = output.lines()
                    .filter(line -> !line.isBlank())
                    .map(Path::of)
                    .toList();

            if (paths.isEmpty()) {
                return "";
            }

            // Post-sort by modification time to match pure Java output ordering
            List<Path> sorted = new ArrayList<>(paths);
            sorted.sort(Comparator
                    .<Path, FileTime>comparing(p -> {
                        try {
                            return Files.getLastModifiedTime(p);
                        } catch (IOException e) {
                            return FileTime.fromMillis(0);
                        }
                    }).reversed());

            if (headLimit != null && sorted.size() > headLimit) {
                sorted = sorted.subList(0, headLimit);
            }

            StringBuilder sb = new StringBuilder();
            for (Path p : sorted) {
                sb.append(p.toAbsolutePath()).append('\n');
            }
            sb.setLength(sb.length() - 1);
            return sb.toString();
        } catch (IOException | InterruptedException e) {
            return null;
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
