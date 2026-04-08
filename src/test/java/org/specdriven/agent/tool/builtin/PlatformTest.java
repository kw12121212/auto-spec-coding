package org.specdriven.agent.tool.builtin;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PlatformTest {

    @Test
    void detectReturnsCurrentPlatform() {
        // Should not throw on supported platforms
        Platform platform = Platform.detect();
        assertNotNull(platform);
        assertNotNull(platform.os());
        assertNotNull(platform.arch());
    }

    @Test
    void resourceDirFormat() {
        Platform linuxX86 = new Platform(OperatingSystem.LINUX, Architecture.X86_64);
        assertEquals("linux-x86_64", linuxX86.resourceDir());

        Platform macArm = new Platform(OperatingSystem.MACOS, Architecture.ARM64);
        assertEquals("macos-arm64", macArm.resourceDir());

        Platform macX86 = new Platform(OperatingSystem.MACOS, Architecture.X86_64);
        assertEquals("macos-x86_64", macX86.resourceDir());
    }

    @Test
    void architectureSuffixes() {
        assertEquals("x86_64", Architecture.X86_64.suffix());
        assertEquals("arm64", Architecture.ARM64.suffix());
    }
}
