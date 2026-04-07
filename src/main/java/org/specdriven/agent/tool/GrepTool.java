package org.specdriven.agent.tool;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

import org.specdriven.agent.permission.Permission;

/**
 * Tool that searches file contents using regular expression patterns.
 */
public class GrepTool implements Tool {

    private static final String NAME = "grep";
    private static final int BINARY_CHECK_SIZE = 8192;

    private static final List<ToolParameter> PARAMETERS = List.of(
            new ToolParameter("pattern", "string", "Regular expression pattern to search for", true),
            new ToolParameter("path", "string", "Root directory to search in (default: context workDir)", false),
            new ToolParameter("glob", "string", "Glob pattern to filter files (e.g. \"*.java\")", false),
            new ToolParameter("output_mode", "string", "Output format: content, files_with_matches, count (default: content)", false),
            new ToolParameter("case_insensitive", "boolean", "Whether to perform case-insensitive matching (default: false)", false),
            new ToolParameter("context", "integer", "Number of context lines before and after each match", false),
            new ToolParameter("head_limit", "integer", "Maximum number of result entries to return", false)
    );

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Searches file contents using regular expression patterns across directory trees";
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

        // Compile regex
        Pattern pattern;
        boolean caseInsensitive = booleanParam(input, "case_insensitive");
        int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0;
        try {
            pattern = Pattern.compile(patternStr, flags);
        } catch (PatternSyntaxException e) {
            return new ToolResult.Error("Invalid regex pattern: " + e.getMessage());
        }

        // Resolve search root
        String pathStr = stringParam(input, "path", context.workDir());
        Path searchRoot = resolvePath(pathStr, context.workDir());
        if (!Files.exists(searchRoot) || !Files.isDirectory(searchRoot)) {
            return new ToolResult.Error("Search path does not exist or is not a directory: " + searchRoot);
        }

        // Resolve output mode
        String outputMode = stringParam(input, "output_mode", "content");
        Integer contextLines = intParam(input, "context");
        Integer headLimit = intParam(input, "head_limit");

        // Resolve glob filter
        String globStr = stringParam(input, "glob");
        PathMatcher pathMatcher = null;
        if (globStr != null) {
            pathMatcher = searchRoot.getFileSystem().getPathMatcher("glob:" + globStr);
        }

        // Execute search
        try {
            String result = switch (outputMode) {
                case "files_with_matches" -> searchFilesWithMatches(searchRoot, pattern, pathMatcher, headLimit);
                case "count" -> searchCount(searchRoot, pattern, pathMatcher, headLimit);
                default -> searchContent(searchRoot, pattern, pathMatcher, contextLines, headLimit);
            };
            return new ToolResult.Success(result);
        } catch (IOException e) {
            return new ToolResult.Error("Search failed: " + e.getMessage(), e);
        }
    }

    private String searchContent(Path root, Pattern pattern, PathMatcher matcher, Integer contextLines, Integer headLimit) throws IOException {
        List<MatchLine> matches = new ArrayList<>();
        int count = 0;

        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> files = walk.filter(Files::isRegularFile)
                    .filter(p -> !Files.isSymbolicLink(p))
                    .filter(p -> matcher == null || matcher.matches(root.relativize(p)))
                    .toList();

            for (Path file : files) {
                if (headLimit != null && count >= headLimit) break;
                if (isBinary(file)) continue;

                List<String> lines = readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    if (headLimit != null && count >= headLimit) break;
                    if (pattern.matcher(lines.get(i)).find()) {
                        matches.add(new MatchLine(file.toString(), i + 1, lines.get(i)));
                        count++;
                    }
                }
            }
        }

        if (contextLines == null || contextLines <= 0) {
            return formatContent(matches);
        }
        return formatContentWithContext(root, matches, pattern, contextLines);
    }

    private String searchFilesWithMatches(Path root, Pattern pattern, PathMatcher matcher, Integer headLimit) throws IOException {
        Set<String> found = new LinkedHashSet<>();
        int count = 0;

        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> files = walk.filter(Files::isRegularFile)
                    .filter(p -> !Files.isSymbolicLink(p))
                    .filter(p -> matcher == null || matcher.matches(root.relativize(p)))
                    .toList();

            for (Path file : files) {
                if (headLimit != null && count >= headLimit) break;
                if (isBinary(file)) continue;

                try {
                    if (fileContainsMatch(file, pattern)) {
                        found.add(file.toString());
                        count++;
                    }
                } catch (IOException ignored) {}
            }
        }

        return String.join("\n", found);
    }

    private String searchCount(Path root, Pattern pattern, PathMatcher matcher, Integer headLimit) throws IOException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        int fileCount = 0;

        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> files = walk.filter(Files::isRegularFile)
                    .filter(p -> !Files.isSymbolicLink(p))
                    .filter(p -> matcher == null || matcher.matches(root.relativize(p)))
                    .toList();

            for (Path file : files) {
                if (headLimit != null && fileCount >= headLimit) break;
                if (isBinary(file)) continue;

                try {
                    int matchCount = countMatches(file, pattern);
                    if (matchCount > 0) {
                        counts.put(file.toString(), matchCount);
                        fileCount++;
                    }
                } catch (IOException ignored) {}
            }
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            sb.append(entry.getKey()).append(':').append(entry.getValue()).append('\n');
        }
        if (!sb.isEmpty()) {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    private boolean isBinary(Path file) {
        try (var is = Files.newInputStream(file)) {
            byte[] bytes = is.readNBytes(BINARY_CHECK_SIZE);
            for (byte b : bytes) {
                if (b == 0) return true;
            }
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    private boolean fileContainsMatch(Path file, Pattern pattern) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (pattern.matcher(line).find()) return true;
            }
        }
        return false;
    }

    private int countMatches(Path file, Pattern pattern) throws IOException {
        int count = 0;
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (pattern.matcher(line).find()) count++;
            }
        }
        return count;
    }

    private List<String> readAllLines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException e) {
            return List.of();
        }
    }

    private String formatContent(List<MatchLine> matches) {
        StringBuilder sb = new StringBuilder();
        for (MatchLine m : matches) {
            sb.append(m.filePath).append(':').append(m.lineNumber).append(':').append(m.content).append('\n');
        }
        if (!sb.isEmpty()) {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    private String formatContentWithContext(Path root, List<MatchLine> matches, Pattern pattern, int contextLines) {
        // Group matches by file
        Map<String, List<MatchLine>> byFile = new LinkedHashMap<>();
        for (MatchLine m : matches) {
            byFile.computeIfAbsent(m.filePath, k -> new ArrayList<>()).add(m);
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<MatchLine>> entry : byFile.entrySet()) {
            String filePath = entry.getKey();
            List<MatchLine> fileMatches = entry.getValue();
            List<String> allLines = readAllLines(Path.of(filePath));

            // Collect all line numbers to show (matches + context)
            Set<Integer> shownLines = new LinkedHashSet<>();
            for (MatchLine m : fileMatches) {
                for (int i = Math.max(1, m.lineNumber - contextLines); i <= Math.min(allLines.size(), m.lineNumber + contextLines); i++) {
                    shownLines.add(i);
                }
            }

            for (int lineNum : shownLines) {
                String content = lineNum - 1 < allLines.size() ? allLines.get(lineNum - 1) : "";
                boolean isMatch = fileMatches.stream().anyMatch(m -> m.lineNumber == lineNum);
                if (isMatch) {
                    sb.append(filePath).append(':').append(lineNum).append(':').append(content);
                } else {
                    sb.append(filePath).append('-').append(lineNum).append('-').append(content);
                }
                sb.append('\n');
            }
        }

        if (!sb.isEmpty()) {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    static Path resolvePath(String raw, String workDir) {
        Path path = Path.of(raw);
        if (!path.isAbsolute()) {
            path = Path.of(workDir).resolve(path);
        }
        return path.normalize();
    }

    private static String stringParam(ToolInput input, String name) {
        return stringParam(input, name, null);
    }

    private static String stringParam(ToolInput input, String name, String defaultValue) {
        Object value = input.parameters().get(name);
        return value != null ? value.toString() : defaultValue;
    }

    private static boolean booleanParam(ToolInput input, String name) {
        Object value = input.parameters().get(name);
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private static Integer intParam(ToolInput input, String name) {
        Object value = input.parameters().get(name);
        return value != null ? ((Number) value).intValue() : null;
    }

    private record MatchLine(String filePath, int lineNumber, String content) {}
}
