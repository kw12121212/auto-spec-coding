package org.specdriven.agent.tool;

/**
 * Point-in-time snapshot of a background process's output.
 *
 * @param stdout    captured standard output
 * @param stderr    captured standard error
 * @param exitCode  process exit code, or -1 if still running
 * @param timestamp epoch millis when this snapshot was taken
 */
public record ProcessOutput(
        String stdout,
        String stderr,
        int exitCode,
        long timestamp
) {}
