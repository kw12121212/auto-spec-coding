package org.specdriven.agent.tool.builtin;

/**
 * Represents the detected OS and CPU architecture for selecting
 * the correct release artifact.
 */
public record Platform(OperatingSystem os, Architecture arch) {

    /**
     * Detects the current platform from system properties.
     *
     * @throws UnsupportedOperationException if the OS or architecture is not supported
     */
    public static Platform detect() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        String osArch = System.getProperty("os.arch", "").toLowerCase();

        OperatingSystem os = detectOs(osName);
        Architecture arch = detectArch(osArch);

        return new Platform(os, arch);
    }

    private static OperatingSystem detectOs(String osName) {
        if (osName.contains("linux")) return OperatingSystem.LINUX;
        if (osName.contains("mac") || osName.contains("darwin")) return OperatingSystem.MACOS;
        throw new UnsupportedOperationException("Unsupported OS: " + osName);
    }

    private static Architecture detectArch(String osArch) {
        if (osArch.equals("aarch64") || osArch.equals("arm64")) return Architecture.ARM64;
        if (osArch.equals("amd64") || osArch.equals("x86_64") || osArch.equals("x86_64")) return Architecture.X86_64;
        throw new UnsupportedOperationException("Unsupported architecture: " + osArch);
    }

    /** Returns the resource directory name (e.g. "linux-x86_64", "macos-arm64"). */
    public String resourceDir() {
        return os.name().toLowerCase() + "-" + arch.suffix();
    }
}
