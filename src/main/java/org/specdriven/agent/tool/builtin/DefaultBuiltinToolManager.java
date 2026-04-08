package org.specdriven.agent.tool.builtin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Default implementation of {@link BuiltinToolManager}.
 * Extracts bundled tool binaries from classpath resources to a local cache directory.
 * Falls back to PATH detection if the resource is not available (e.g. wrong platform jar).
 */
public class DefaultBuiltinToolManager implements BuiltinToolManager {

    private final Path cacheDir;
    private final Platform platform;

    /**
     * Creates a manager with the default cache directory ({@code ~/.specdriven/bin/}).
     */
    public DefaultBuiltinToolManager() {
        this(defaultCacheDir());
    }

    /**
     * Creates a manager with a custom cache directory.
     *
     * @param cacheDir the directory to store extracted binaries
     */
    public DefaultBuiltinToolManager(Path cacheDir) {
        this.cacheDir = cacheDir;
        this.platform = Platform.detect();
    }

    @Override
    public Path resolve(BuiltinTool tool) {
        // 1. Check local cache
        Path cached = cacheDir.resolve(tool.binaryName());
        if (Files.isExecutable(cached)) {
            return cached;
        }

        // 2. Check system PATH
        Optional<Path> onPath = detectOnPath(tool);
        if (onPath.isPresent()) {
            return onPath.get();
        }

        // 3. Extract from classpath resources
        return extractFromResources(tool);
    }

    @Override
    public Optional<Path> detect(BuiltinTool tool) {
        // 1. Check local cache
        Path cached = cacheDir.resolve(tool.binaryName());
        if (Files.isExecutable(cached)) {
            return Optional.of(cached);
        }

        // 2. Check system PATH
        return detectOnPath(tool);
    }

    @Override
    public Path cacheDir() {
        return cacheDir;
    }

    private Path extractFromResources(BuiltinTool tool) {
        String resourcePath = tool.resourcePath(platform);
        InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (resourceStream == null) {
            throw new BuiltinToolException(
                    "Bundled binary not found for " + tool.toolName()
                            + " on " + platform.resourceDir()
                            + ". Ensure the correct platform-specific jar is on the classpath.");
        }

        try {
            Files.createDirectories(cacheDir);
            Path target = cacheDir.resolve(tool.binaryName());
            try (InputStream is = resourceStream) {
                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            }
            target.toFile().setExecutable(true);
            return target;
        } catch (IOException e) {
            throw new BuiltinToolException(
                    "Failed to extract " + tool.toolName() + ": " + e.getMessage(), e);
        }
    }

    private Optional<Path> detectOnPath(BuiltinTool tool) {
        String binaryName = tool.binaryName();
        String command = System.getProperty("os.name", "").toLowerCase().contains("windows")
                ? "where" : "which";

        try {
            ProcessBuilder pb = new ProcessBuilder(command, binaryName);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            int exitCode = process.waitFor();
            if (exitCode == 0 && !output.isEmpty()) {
                String firstLine = output.split("\n")[0].trim();
                Path path = Path.of(firstLine);
                if (Files.exists(path)) {
                    return Optional.of(path);
                }
            }
        } catch (IOException | InterruptedException e) {
            // Tool not found on PATH — expected
        }
        return Optional.empty();
    }

    private static Path defaultCacheDir() {
        String home = System.getProperty("user.home");
        return Path.of(home, ".specdriven", "bin");
    }
}
