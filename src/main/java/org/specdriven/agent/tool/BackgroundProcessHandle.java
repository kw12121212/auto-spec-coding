package org.specdriven.agent.tool;

import java.util.UUID;

/**
 * Immutable snapshot of a managed background process's metadata.
 *
 * @param id        unique identifier (randomly generated UUID)
 * @param pid       OS process ID, or -1 if unavailable
 * @param command   the command that started the process
 * @param toolName  name of the tool that launched the process
 * @param startTime epoch millis when the process was started
 * @param state     current lifecycle state of the process
 */
public record BackgroundProcessHandle(
        String id,
        long pid,
        String command,
        String toolName,
        long startTime,
        ProcessState state
) {
    public BackgroundProcessHandle {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
    }

    /**
     * Creates a BackgroundProcessHandle with a randomly generated ID.
     */
    public BackgroundProcessHandle(long pid, String command, String toolName, long startTime, ProcessState state) {
        this(null, pid, command, toolName, startTime, state);
    }
}
