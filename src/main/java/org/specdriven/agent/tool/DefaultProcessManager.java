package org.specdriven.agent.tool;

import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default thread-safe implementation of {@link ProcessManager}.
 * <p>
 * Tracks background processes using a {@link ConcurrentHashMap}, collects stdout/stderr
 * via virtual threads into bounded ring buffers, and emits lifecycle events via {@link EventBus}.
 */
public class DefaultProcessManager implements ProcessManager {

    private static final int DEFAULT_MAX_OUTPUT_BYTES = 1048576; // 1MB

    private final EventBus eventBus;
    private final int maxOutputBytes;
    private final ConcurrentHashMap<String, ManagedProcess> processes = new ConcurrentHashMap<>();

    public DefaultProcessManager(EventBus eventBus) {
        this(eventBus, DEFAULT_MAX_OUTPUT_BYTES);
    }

    public DefaultProcessManager(EventBus eventBus, int maxOutputBytes) {
        this.eventBus = eventBus;
        this.maxOutputBytes = maxOutputBytes;
    }

    @Override
    public BackgroundProcessHandle register(Process process, String toolName, String command) {
        long pid = process.pid();
        long startTime = System.currentTimeMillis();
        String id = java.util.UUID.randomUUID().toString();

        BackgroundProcessHandle handle = new BackgroundProcessHandle(
                id, pid, command, toolName, startTime, ProcessState.STARTING
        );

        OutputRingBuffer stdoutBuffer = new OutputRingBuffer(maxOutputBytes);
        OutputRingBuffer stderrBuffer = new OutputRingBuffer(maxOutputBytes);
        ProcessStateHolder stateHolder = new ProcessStateHolder(ProcessState.STARTING);

        ManagedProcess managed = new ManagedProcess(process, handle, stdoutBuffer, stderrBuffer, stateHolder);
        processes.put(id, managed);

        // Start output reader virtual threads
        Thread.ofVirtual().name("stdout-reader-" + id).start(() -> readStream(
                new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)),
                stdoutBuffer
        ));
        Thread.ofVirtual().name("stderr-reader-" + id).start(() -> readStream(
                new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)),
                stderrBuffer
        ));

        // Transition to RUNNING now that readers are active
        stateHolder.set(ProcessState.RUNNING);
        handle = new BackgroundProcessHandle(id, pid, command, toolName, startTime, ProcessState.RUNNING);
        managed.handle = handle;

        // Start exit monitor
        Thread.ofVirtual().name("exit-monitor-" + id).start(() -> {
            try {
                int exitCode = process.waitFor();
                ProcessState terminalState = (exitCode == 0) ? ProcessState.COMPLETED : ProcessState.FAILED;
                stateHolder.set(terminalState);
                managed.handle = new BackgroundProcessHandle(id, pid, command, toolName, startTime, terminalState);
                managed.exitCode = exitCode;

                eventBus.publish(new Event(
                        EventType.BACKGROUND_TOOL_STOPPED,
                        System.currentTimeMillis(),
                        toolName,
                        Map.of("processId", id, "exitCode", String.valueOf(exitCode))
                ));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Emit started event
        eventBus.publish(new Event(
                EventType.BACKGROUND_TOOL_STARTED,
                System.currentTimeMillis(),
                toolName,
                Map.of("processId", id, "toolName", toolName, "command", command)
        ));

        return handle;
    }

    @Override
    public Optional<ProcessState> getState(String processId) {
        ManagedProcess managed = processes.get(processId);
        return managed != null ? Optional.of(managed.stateHolder.get()) : Optional.empty();
    }

    @Override
    public Optional<ProcessOutput> getOutput(String processId) {
        ManagedProcess managed = processes.get(processId);
        if (managed == null) return Optional.empty();

        ProcessState state = managed.stateHolder.get();
        boolean running = state == ProcessState.STARTING || state == ProcessState.RUNNING;
        int exitCode = running ? -1 : managed.exitCode;

        return Optional.of(new ProcessOutput(
                managed.stdoutBuffer.snapshot(),
                managed.stderrBuffer.snapshot(),
                exitCode,
                System.currentTimeMillis()
        ));
    }

    @Override
    public List<BackgroundProcessHandle> listActive() {
        List<BackgroundProcessHandle> active = new ArrayList<>();
        for (ManagedProcess managed : processes.values()) {
            ProcessState state = managed.stateHolder.get();
            if (state == ProcessState.STARTING || state == ProcessState.RUNNING) {
                active.add(managed.handle);
            }
        }
        return active;
    }

    @Override
    public boolean stop(String processId) {
        ManagedProcess managed = processes.get(processId);
        if (managed == null) return false;

        ProcessState state = managed.stateHolder.get();
        if (state != ProcessState.STARTING && state != ProcessState.RUNNING) {
            return false;
        }

        managed.process.toHandle().destroy();
        managed.stateHolder.set(ProcessState.STOPPED);
        managed.handle = new BackgroundProcessHandle(
                managed.handle.id(),
                managed.handle.pid(),
                managed.handle.command(),
                managed.handle.toolName(),
                managed.handle.startTime(),
                ProcessState.STOPPED
        );
        managed.exitCode = -1;

        eventBus.publish(new Event(
                EventType.BACKGROUND_TOOL_STOPPED,
                System.currentTimeMillis(),
                managed.handle.toolName(),
                Map.of("processId", processId, "exitCode", String.valueOf(-1))
        ));

        return true;
    }

    @Override
    public int stopAll() {
        int count = 0;
        for (String processId : processes.keySet()) {
            if (stop(processId)) {
                count++;
            }
        }
        return count;
    }

    private void readStream(BufferedReader reader, OutputRingBuffer buffer) {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
                buffer.append("\n");
            }
        } catch (IOException ignored) {
            // Stream closed — process terminated
        }
    }

    // --- Internal state holders ---

    static final class ManagedProcess {
        final Process process;
        volatile BackgroundProcessHandle handle;
        final OutputRingBuffer stdoutBuffer;
        final OutputRingBuffer stderrBuffer;
        final ProcessStateHolder stateHolder;
        volatile int exitCode = -1;

        ManagedProcess(Process process, BackgroundProcessHandle handle,
                       OutputRingBuffer stdoutBuffer, OutputRingBuffer stderrBuffer,
                       ProcessStateHolder stateHolder) {
            this.process = process;
            this.handle = handle;
            this.stdoutBuffer = stdoutBuffer;
            this.stderrBuffer = stderrBuffer;
            this.stateHolder = stateHolder;
        }
    }

    static final class ProcessStateHolder {
        private volatile ProcessState state;

        ProcessStateHolder(ProcessState initial) {
            this.state = initial;
        }

        ProcessState get() {
            return state;
        }

        void set(ProcessState state) {
            this.state = state;
        }
    }
}
