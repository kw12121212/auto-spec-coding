package org.specdriven.agent.tool.builtin;

/**
 * Supported external tools bundled as platform-specific resources.
 * Each constant carries metadata for resource lookup and resolution.
 */
public enum BuiltinTool {

    RG("ripgrep", "rg", "15.1.0"),
    FD("fd-find", "fd", "10.4.2");

    private final String toolName;
    private final String binaryName;
    private final String versionTag;

    BuiltinTool(String toolName, String binaryName, String versionTag) {
        this.toolName = toolName;
        this.binaryName = binaryName;
        this.versionTag = versionTag;
    }

    /** Human-readable tool name (e.g. "ripgrep"). */
    public String toolName() {
        return toolName;
    }

    /** Platform-appropriate binary name (e.g. "rg"). */
    public String binaryName() {
        return binaryName;
    }

    /** Pinned version tag (e.g. "14.1.0"). */
    public String versionTag() {
        return versionTag;
    }

    /**
     * Returns the classpath resource path for this tool on the given platform.
     * e.g. "builtin-tools/linux-x86_64/rg"
     */
    public String resourcePath(Platform platform) {
        return "builtin-tools/" + platform.resourceDir() + "/" + binaryName;
    }
}
