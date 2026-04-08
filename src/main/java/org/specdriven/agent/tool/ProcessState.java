package org.specdriven.agent.tool;

/**
 * Lifecycle states of a background process managed by the agent.
 */
public enum ProcessState {
    /** Process has been requested but not yet confirmed running. */
    STARTING,
    /** Process is actively executing. */
    RUNNING,
    /** Process exited normally (exit code 0). */
    COMPLETED,
    /** Process exited with a non-zero exit code. */
    FAILED,
    /** Process was terminated by user or system action. */
    STOPPED
}
