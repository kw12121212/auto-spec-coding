package org.specdriven.agent.tool.builtin;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BuiltinToolTest {

    @Test
    void enumContainsRGandFD() {
        assertArrayEquals(new BuiltinTool[]{BuiltinTool.RG, BuiltinTool.FD},
                BuiltinTool.values());
    }

    @Test
    void rgHasNonBlankMetadata() {
        BuiltinTool rg = BuiltinTool.RG;
        assertFalse(rg.toolName().isBlank());
        assertFalse(rg.binaryName().isBlank());
        assertFalse(rg.versionTag().isBlank());
        assertEquals("ripgrep", rg.toolName());
        assertEquals("rg", rg.binaryName());
    }

    @Test
    void fdHasNonBlankMetadata() {
        BuiltinTool fd = BuiltinTool.FD;
        assertFalse(fd.toolName().isBlank());
        assertFalse(fd.binaryName().isBlank());
        assertFalse(fd.versionTag().isBlank());
        assertEquals("fd-find", fd.toolName());
        assertEquals("fd", fd.binaryName());
    }

    @Test
    void resourcePathFormat() {
        Platform linuxX86 = new Platform(OperatingSystem.LINUX, Architecture.X86_64);
        Platform macArm = new Platform(OperatingSystem.MACOS, Architecture.ARM64);

        assertEquals("builtin-tools/linux-x86_64/rg", BuiltinTool.RG.resourcePath(linuxX86));
        assertEquals("builtin-tools/macos-arm64/fd", BuiltinTool.FD.resourcePath(macArm));
    }

    @Test
    void versionTagIsSemver() {
        for (BuiltinTool tool : BuiltinTool.values()) {
            assertTrue(tool.versionTag().matches("\\d+\\.\\d+\\.\\d+"),
                    tool.name() + " version should be semver: " + tool.versionTag());
        }
    }
}
