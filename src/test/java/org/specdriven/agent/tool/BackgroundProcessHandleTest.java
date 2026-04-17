package org.specdriven.agent.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class BackgroundProcessHandleTest {

    @Test
    void constructorGeneratesUuidWhenNull() {
        BackgroundProcessHandle handle = new BackgroundProcessHandle(null, 1234, "ls -la", "bash",
                System.currentTimeMillis(), ProcessState.RUNNING, null);
        assertNotNull(handle.id());
        assertDoesNotThrow(() -> UUID.fromString(handle.id()));
    }

    @Test
    void constructorGeneratesUuidWhenBlank() {
        BackgroundProcessHandle handle = new BackgroundProcessHandle("  ", 1234, "ls", "bash",
                System.currentTimeMillis(), ProcessState.RUNNING, null);
        assertNotNull(handle.id());
        assertDoesNotThrow(() -> UUID.fromString(handle.id()));
    }

    @Test
    void constructorPreservesValidId() {
        String explicitId = UUID.randomUUID().toString();
        BackgroundProcessHandle handle = new BackgroundProcessHandle(explicitId, 1234, "ls", "bash",
                System.currentTimeMillis(), ProcessState.RUNNING, null);
        assertEquals(explicitId, handle.id());
    }

    @Test
    void convenienceConstructorGeneratesId() {
        BackgroundProcessHandle handle = new BackgroundProcessHandle(5678, "make", "bash",
                System.currentTimeMillis(), ProcessState.STARTING);
        assertNotNull(handle.id());
        assertEquals(5678, handle.pid());
        assertEquals("make", handle.command());
        assertEquals("bash", handle.toolName());
        assertEquals(ProcessState.STARTING, handle.state());
    }

    @Test
    void recordImmutability() {
        long ts = System.currentTimeMillis();
        BackgroundProcessHandle handle = new BackgroundProcessHandle(99L, "cmd", "tool", ts, ProcessState.RUNNING,
                "dev");
        assertEquals(99L, handle.pid());
        assertEquals("cmd", handle.command());
        assertEquals("tool", handle.toolName());
        assertEquals(ts, handle.startTime());
        assertEquals(ProcessState.RUNNING, handle.state());
        assertEquals("dev", handle.resolvedProfile());
    }

    @Test
    void negativePidForUnavailable() {
        BackgroundProcessHandle handle = new BackgroundProcessHandle(-1, "cmd", "tool",
                System.currentTimeMillis(), ProcessState.STARTING);
        assertEquals(-1, handle.pid());
        assertNull(handle.resolvedProfile());
    }

    @Test
    void constructorPreservesResolvedProfileWhenProvided() {
        BackgroundProcessHandle handle = new BackgroundProcessHandle(
                "id-1", 42L, "cmd", "tool", System.currentTimeMillis(), ProcessState.RUNNING, "ci");

        assertEquals("ci", handle.resolvedProfile());
    }
}
