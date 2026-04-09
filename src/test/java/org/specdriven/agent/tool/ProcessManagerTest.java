package org.specdriven.agent.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;

class ProcessManagerTest {

    /**
     * Capturing EventBus stub for testing event emission.
     */
    static class CapturingEventBus implements EventBus {
        final List<Event> events = new ArrayList<>();

        @Override
        public void publish(Event event) {
            events.add(event);
        }

        @Override
        public void subscribe(EventType type, java.util.function.Consumer<Event> listener) {}

        @Override
        public void unsubscribe(EventType type, java.util.function.Consumer<Event> listener) {}

        List<Event> eventsOfType(EventType type) {
            return events.stream().filter(e -> e.type() == type).toList();
        }
    }

    private Process launchSleepProcess(int seconds) throws Exception {
        return new ProcessBuilder("sleep", String.valueOf(seconds)).start();
    }

    private Process launchEchoProcess(String output) throws Exception {
        return new ProcessBuilder("sh", "-c", "echo " + output).start();
    }

    private Process launchFailingProcess() throws Exception {
        return new ProcessBuilder("sh", "-c", "echo err >&2; exit 1").start();
    }

    // --- Task: register() creates handle with correct fields and emits BACKGROUND_TOOL_STARTED ---

    @Test
    void registerCreatesHandleWithCorrectFields() throws Exception {
        CapturingEventBus bus = new CapturingEventBus();
        DefaultProcessManager pm = new DefaultProcessManager(bus);

        Process process = launchSleepProcess(30);
        BackgroundProcessHandle handle = pm.register(process, "test-tool", "sleep 30");
        try {
            assertNotNull(handle.id());
            assertFalse(handle.id().isBlank());
            assertTrue(handle.pid() > 0);
            assertEquals("sleep 30", handle.command());
            assertEquals("test-tool", handle.toolName());
            assertTrue(handle.startTime() > 0);
            assertEquals(ProcessState.RUNNING, handle.state());
        } finally {
            pm.stop(handle.id());
        }
    }

    @Test
    void registerEmitsBackgroundToolStartedEvent() throws Exception {
        CapturingEventBus bus = new CapturingEventBus();
        DefaultProcessManager pm = new DefaultProcessManager(bus);

        Process process = launchSleepProcess(30);
        BackgroundProcessHandle handle = pm.register(process, "test-tool", "sleep 30");
        try {
            List<Event> started = bus.eventsOfType(EventType.BACKGROUND_TOOL_STARTED);
            assertEquals(1, started.size());
            assertEquals(handle.id(), started.get(0).metadata().get("processId"));
            assertEquals("test-tool", started.get(0).metadata().get("toolName"));
            assertEquals("sleep 30", started.get(0).metadata().get("command"));
        } finally {
            pm.stop(handle.id());
        }
    }

    // --- Task: getState() returns correct state transitions ---

    @Test
    void getStateTransitionsToCompletedOnExit() throws Exception {
        CapturingEventBus bus = new CapturingEventBus();
        DefaultProcessManager pm = new DefaultProcessManager(bus);

        Process process = launchEchoProcess("hello");
        BackgroundProcessHandle handle = pm.register(process, "echo-tool", "echo hello");

        // Wait for process to exit
        process.waitFor();
        Thread.sleep(200); // Allow exit monitor to update state

        Optional<ProcessState> state = pm.getState(handle.id());
        assertTrue(state.isPresent());
        assertEquals(ProcessState.COMPLETED, state.get());
    }

    @Test
    void getStateTransitionsToFailedOnNonZeroExit() throws Exception {
        CapturingEventBus bus = new CapturingEventBus();
        DefaultProcessManager pm = new DefaultProcessManager(bus);

        Process process = launchFailingProcess();
        BackgroundProcessHandle handle = pm.register(process, "fail-tool", "exit 1");

        process.waitFor();
        Thread.sleep(200);

        Optional<ProcessState> state = pm.getState(handle.id());
        assertTrue(state.isPresent());
        assertEquals(ProcessState.FAILED, state.get());
    }

    // --- Task: getOutput() returns accumulated stdout/stderr ---

    @Test
    void getOutputReturnsAccumulatedStdout() throws Exception {
        CapturingEventBus bus = new CapturingEventBus();
        DefaultProcessManager pm = new DefaultProcessManager(bus);

        Process process = launchEchoProcess("hello world");
        BackgroundProcessHandle handle = pm.register(process, "echo-tool", "echo hello world");

        process.waitFor();
        Thread.sleep(300); // Allow output reader to finish

        Optional<ProcessOutput> output = pm.getOutput(handle.id());
        assertTrue(output.isPresent());
        assertTrue(output.get().stdout().contains("hello world"));
    }

    @Test
    void getOutputReturnsStderr() throws Exception {
        CapturingEventBus bus = new CapturingEventBus();
        DefaultProcessManager pm = new DefaultProcessManager(bus);

        Process process = launchFailingProcess();
        BackgroundProcessHandle handle = pm.register(process, "fail-tool", "echo err >&2; exit 1");

        process.waitFor();
        Thread.sleep(300);

        Optional<ProcessOutput> output = pm.getOutput(handle.id());
        assertTrue(output.isPresent());
        assertTrue(output.get().stderr().contains("err"));
    }

    @Test
    void getOutputForRunningProcessReturnsExitCodeMinusOne() throws Exception {
        CapturingEventBus bus = new CapturingEventBus();
        DefaultProcessManager pm = new DefaultProcessManager(bus);

        Process process = launchSleepProcess(30);
        BackgroundProcessHandle handle = pm.register(process, "sleep-tool", "sleep 30");
        try {
            Optional<ProcessOutput> output = pm.getOutput(handle.id());
            assertTrue(output.isPresent());
            assertEquals(-1, output.get().exitCode());
        } finally {
            pm.stop(handle.id());
        }
    }

    @Test
    void getOutputForCompletedProcessReturnsActualExitCode() throws Exception {
        CapturingEventBus bus = new CapturingEventBus();
        DefaultProcessManager pm = new DefaultProcessManager(bus);

        Process process = launchEchoProcess("done");
        BackgroundProcessHandle handle = pm.register(process, "echo-tool", "echo done");

        process.waitFor();
        Thread.sleep(300);

        Optional<ProcessOutput> output = pm.getOutput(handle.id());
        assertTrue(output.isPresent());
        assertEquals(0, output.get().exitCode());
    }

    // --- Task: output tail-truncation when buffer exceeds maxOutputBytes ---

    @Test
    void outputTailTruncationWhenExceedsBuffer() throws Exception {
        CapturingEventBus bus = new CapturingEventBus();
        int smallBuffer = 64;
        DefaultProcessManager pm = new DefaultProcessManager(bus, smallBuffer);

        // Generate more output than the buffer can hold
        Process process = new ProcessBuilder("sh", "-c",
                "for i in $(seq 1 20); do echo \"line_${i}_output_padding_here\"; done").start();
        BackgroundProcessHandle handle = pm.register(process, "gen-tool", "generate output");

        process.waitFor();
        // Poll until output reader collects data or timeout
        Optional<ProcessOutput> output = Optional.empty();
        for (int i = 0; i < 20; i++) {
            Thread.sleep(100);
            output = pm.getOutput(handle.id());
            if (output.isPresent() && output.get().stdout().contains("line_20")) {
                break;
            }
        }

        assertTrue(output.isPresent());
        assertFalse(output.get().stdout().isEmpty());
        assertTrue(output.get().stdout().contains("line_20"),
                "Expected tail to contain line_20, got: " + output.get().stdout());
        // Total bytes should not exceed buffer by much (ring buffer cap)
        assertTrue(output.get().stdout().getBytes().length <= smallBuffer + 100,
                "Output bytes should be bounded near capacity");
    }

    // --- Task: stop() transitions to STOPPED and emits event ---

    @Test
    void stopTransitionsToStopped() throws Exception {
        CapturingEventBus bus = new CapturingEventBus();
        DefaultProcessManager pm = new DefaultProcessManager(bus);

        Process process = launchSleepProcess(60);
        BackgroundProcessHandle handle = pm.register(process, "sleep-tool", "sleep 60");

        boolean result = pm.stop(handle.id());
        assertTrue(result);

        Optional<ProcessState> state = pm.getState(handle.id());
        assertTrue(state.isPresent());
        assertEquals(ProcessState.STOPPED, state.get());
    }

    @Test
    void stopEmitsBackgroundToolStoppedEvent() throws Exception {
        CapturingEventBus bus = new CapturingEventBus();
        DefaultProcessManager pm = new DefaultProcessManager(bus);

        Process process = launchSleepProcess(60);
        BackgroundProcessHandle handle = pm.register(process, "sleep-tool", "sleep 60");

        pm.stop(handle.id());

        List<Event> stopped = bus.eventsOfType(EventType.BACKGROUND_TOOL_STOPPED);
        assertFalse(stopped.isEmpty());
        Event stopEvent = stopped.get(0);
        assertEquals(handle.id(), stopEvent.metadata().get("processId"));
        assertNotNull(stopEvent.metadata().get("exitCode"));
    }

    // --- Task: stopAll() stops multiple processes ---

    @Test
    void stopAllStopsMultipleActiveProcesses() throws Exception {
        CapturingEventBus bus = new CapturingEventBus();
        DefaultProcessManager pm = new DefaultProcessManager(bus);

        Process p1 = launchSleepProcess(60);
        Process p2 = launchSleepProcess(60);
        pm.register(p1, "tool-1", "sleep 60");
        pm.register(p2, "tool-2", "sleep 60");

        int count = pm.stopAll();
        assertEquals(2, count);

        assertEquals(0, pm.listActive().size());
    }

    // --- Task: listActive() returns only STARTING/RUNNING ---

    @Test
    void listActiveReturnsOnlyRunningProcesses() throws Exception {
        CapturingEventBus bus = new CapturingEventBus();
        DefaultProcessManager pm = new DefaultProcessManager(bus);

        Process running = launchSleepProcess(60);
        Process exiting = launchEchoProcess("fast");

        BackgroundProcessHandle h1 = pm.register(running, "running-tool", "sleep 60");
        BackgroundProcessHandle h2 = pm.register(exiting, "fast-tool", "echo fast");

        // Wait for the fast process to exit
        exiting.waitFor();
        Thread.sleep(300);

        List<BackgroundProcessHandle> active = pm.listActive();
        assertEquals(1, active.size());
        assertEquals(h1.id(), active.get(0).id());

        pm.stop(h1.id());
    }

    // --- Task: getState() and getOutput() return empty for unknown IDs ---

    @Test
    void getStateReturnsEmptyForUnknownId() {
        CapturingEventBus bus = new CapturingEventBus();
        DefaultProcessManager pm = new DefaultProcessManager(bus);

        assertTrue(pm.getState("nonexistent").isEmpty());
    }

    @Test
    void getOutputReturnsEmptyForUnknownId() {
        CapturingEventBus bus = new CapturingEventBus();
        DefaultProcessManager pm = new DefaultProcessManager(bus);

        assertTrue(pm.getOutput("nonexistent").isEmpty());
    }

    @Test
    void stopReturnsFalseForUnknownId() {
        CapturingEventBus bus = new CapturingEventBus();
        DefaultProcessManager pm = new DefaultProcessManager(bus);

        assertFalse(pm.stop("nonexistent"));
    }

    @Test
    void stopReturnsFalseForAlreadyTerminatedProcess() throws Exception {
        CapturingEventBus bus = new CapturingEventBus();
        DefaultProcessManager pm = new DefaultProcessManager(bus);

        Process process = launchEchoProcess("done");
        BackgroundProcessHandle handle = pm.register(process, "echo-tool", "echo done");

        process.waitFor();
        Thread.sleep(300);

        // Process already completed — stop should return false
        assertFalse(pm.stop(handle.id()));
    }

    // --- Thread safety: concurrent access ---

    @Test
    void concurrentAccessIsThreadSafe() throws Exception {
        CapturingEventBus bus = new CapturingEventBus();
        DefaultProcessManager pm = new DefaultProcessManager(bus);

        int count = 5;
        CountDownLatch latch = new CountDownLatch(count);
        List<String> ids = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    Process p = launchSleepProcess(60);
                    BackgroundProcessHandle h = pm.register(p, "concurrent-tool", "sleep 60");
                    synchronized (ids) { ids.add(h.id()); }
                    latch.countDown();
                } catch (Exception e) {
                    fail(e);
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(count, pm.listActive().size());
        assertEquals(count, ids.size());

        pm.stopAll();
    }
}
