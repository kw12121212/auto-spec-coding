package org.specdriven.agent.tool.builtin;

/**
 * Supported CPU architectures for external tool binaries.
 */
public enum Architecture {
    X86_64("x86_64"),
    ARM64("arm64");

    private final String suffix;

    Architecture(String suffix) {
        this.suffix = suffix;
    }

    /** Returns the lowercase suffix used in resource paths. */
    public String suffix() {
        return suffix;
    }
}
