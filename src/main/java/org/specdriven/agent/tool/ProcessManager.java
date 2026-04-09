package org.specdriven.agent.tool;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Manages the lifecycle of background processes launched by {@link BackgroundTool} instances.
 * <p>
 * Provides registration, state tracking, output collection, and termination control
 * for managed processes. Thread-safe for concurrent access.
 */
public interface ProcessManager {

    /**
     * Registers an already-launched process for lifecycle management.
     * Starts output readers and state monitoring.
     *
     * @param process  the running process to manage
     * @param toolName name of the tool that launched the process
     * @param command  the command that started the process
     * @return a handle containing the process metadata
     */
    BackgroundProcessHandle register(Process process, String toolName, String command);

    /**
     * Registers an already-launched process with an associated readiness probe.
     * Behaves identically to {@link #register(Process, String, String)} but stores
     * the probe for later use by {@link #waitForReady(String, Duration)}.
     *
     * @param process  the running process to manage
     * @param toolName name of the tool that launched the process
     * @param command  the command that started the process
     * @param probe    readiness probe configuration, may be null
     * @return a handle containing the process metadata
     */
    BackgroundProcessHandle registerWithProbe(Process process, String toolName, String command, ReadyProbe probe);

    /**
     * Waits for a server tool to become ready by running its readiness probe.
     *
     * @param processId the process ID
     * @param timeout   maximum time to wait
     * @return true if the probe succeeds within the timeout, false otherwise
     */
    boolean waitForReady(String processId, Duration timeout);

    /**
     * Stops the process and releases any server-specific resources.
     * Currently equivalent to {@link #stop(String)} but reserved for future extensions.
     *
     * @param processId the process ID
     * @return true if the process was found and cleaned up, false otherwise
     */
    boolean cleanup(String processId);

    /**
     * Returns the current state of a managed process.
     *
     * @param processId the process ID (from {@link BackgroundProcessHandle#id()})
     * @return the current state, or empty if the process ID is unknown
     */
    Optional<ProcessState> getState(String processId);

    /**
     * Returns a point-in-time snapshot of a process's accumulated output.
     *
     * @param processId the process ID
     * @return the output snapshot, or empty if the process ID is unknown
     */
    Optional<ProcessOutput> getOutput(String processId);

    /**
     * Returns all processes currently in {@link ProcessState#STARTING} or {@link ProcessState#RUNNING}.
     *
     * @return a snapshot list of active process handles
     */
    List<BackgroundProcessHandle> listActive();

    /**
     * Stops a managed process by its ID.
     *
     * @param processId the process ID
     * @return true if the process was found and a stop signal was sent;
     *         false if the process ID is unknown or already terminated
     */
    boolean stop(String processId);

    /**
     * Stops all active (STARTING/RUNNING) processes.
     *
     * @return the count of processes that were successfully stopped
     */
    int stopAll();
}
