package org.specdriven.agent.loop;

/**
 * Status of a single loop iteration.
 */
public enum IterationStatus {
    SUCCESS,
    FAILED,
    SKIPPED,
    TIMED_OUT
}
