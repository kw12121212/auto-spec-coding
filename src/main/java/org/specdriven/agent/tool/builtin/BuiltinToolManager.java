package org.specdriven.agent.tool.builtin;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Manages the lifecycle of external tool binaries: detection, download, and resolution.
 * The manager does NOT execute tools — it only ensures the binary is available on disk
 * and returns its path.
 */
public interface BuiltinToolManager {

    /**
     * Resolves the given tool: returns its path if available locally (cache or PATH),
     * or downloads it from GitHub Releases if missing.
     *
     * @param tool the external tool to resolve
     * @return absolute path to the tool binary
     * @throws BuiltinToolException if the tool cannot be found or downloaded
     */
    Path resolve(BuiltinTool tool);

    /**
     * Detects whether the given tool is available locally (cache or PATH),
     * without triggering a download.
     *
     * @param tool the external tool to detect
     * @return present path if found, empty otherwise
     */
    Optional<Path> detect(BuiltinTool tool);

    /**
     * Returns the local binary cache directory used by this manager.
     */
    Path cacheDir();
}
