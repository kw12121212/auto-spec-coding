package org.specdriven.agent.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;

class ServerToolLifecycleTest {

    static class CapturingEventBus implements EventBus {
        final List<Event> events = new ArrayList<>();

        @Override
        public void publish(Event event) { events.add(event); }

        @Override
        public void subscribe(EventType type, Consumer<Event> listener) {}

        @Override
        public void unsubscribe(EventType type, Consumer<Event> listener) {}

        List<Event> eventsOfType(EventType type) {
            return events.stream().filter(e -> e.type() == type).toList();
        }
    }

    /**
     * Runs a TCP listener in a background thread that accepts one connection.
     */
    private int startTcpListener() throws Exception {
        ServerSocket serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        Thread.ofVirtual().start(() -> {
            try {
                serverSocket.accept().close();
            } catch (Exception ignored) {
            } finally {
                try { serverSocket.close(); } catch (Exception ignored) {}
            }
        });
        return port;
    }

    // --- waitForReady tests ---

    @Test
    void waitForReadyReturnsFalseForUnknownProcess() {
        CapturingEventBus bus = new CapturingEventBus();
        DefaultProcessManager pm = new DefaultProcessManager(bus);
        assertFalse(pm.waitForReady("nonexistent", Duration.ofSeconds(1)));
    }

    @Test
    void waitForReadyReturnsFalseWhenNoProbe() throws Exception {
        CapturingEventBus bus = new CapturingEventBus();
        DefaultProcessManager pm = new DefaultProcessManager(bus);

        Process process = new ProcessBuilder("sleep", "30").start();
        BackgroundProcessHandle handle = pm.register(process, "test-tool", "sleep 30");
        try {
            assertFalse(pm.waitForReady(handle.id(), Duration.ofSeconds(1)));
        } finally {
            pm.stop(handle.id());
        }
    }

    @Test
    void waitForReadySucceedsWithTcpProbe() throws Exception {
        CapturingEventBus bus = new CapturingEventBus();
        DefaultProcessManager pm = new DefaultProcessManager(bus);

        int port = startTcpListener(); // ServerSocket(0) binds synchronously; no sleep needed

        Process sleepProcess = new ProcessBuilder("sleep", "30").start();
        ReadyProbe probe = ReadyProbe.tcp(port);
        BackgroundProcessHandle handle = pm.registerWithProbe(sleepProcess, "server-tool", "sleep 30", probe);
        try {
            boolean ready = pm.waitForReady(handle.id(), Duration.ofSeconds(5));
            assertTrue(ready, "waitForReady should return true when TCP probe succeeds");

            List<Event> readyEvents = bus.eventsOfType(EventType.SERVER_TOOL_READY);
            assertEquals(1, readyEvents.size());
            assertEquals(handle.id(), readyEvents.get(0).metadata().get("processId"));
            assertEquals("server-tool", readyEvents.get(0).metadata().get("toolName"));
            assertEquals("TCP", readyEvents.get(0).metadata().get("probeType"));
        } finally {
            pm.stop(handle.id());
        }
    }

    @Test
    void waitForReadyTimesOutAndEmitsFailedEvent() throws Exception {
        CapturingEventBus bus = new CapturingEventBus();
        DefaultProcessManager pm = new DefaultProcessManager(bus);

        // Use a port that's not listening
        ReadyProbe probe = new ReadyProbe(ProbeType.TCP, "localhost", 59999, null, 200,
                Duration.ofSeconds(1), Duration.ofMillis(100), 3);

        Process sleepProcess = new ProcessBuilder("sleep", "30").start();
        BackgroundProcessHandle handle = pm.registerWithProbe(sleepProcess, "fail-tool", "sleep 30", probe);
        try {
            boolean ready = pm.waitForReady(handle.id(), Duration.ofSeconds(2));
            assertFalse(ready, "waitForReady should return false when probe times out");

            List<Event> failedEvents = bus.eventsOfType(EventType.SERVER_TOOL_FAILED);
            assertEquals(1, failedEvents.size());
            assertEquals(handle.id(), failedEvents.get(0).metadata().get("processId"));
            assertEquals("fail-tool", failedEvents.get(0).metadata().get("toolName"));
            assertEquals("TCP", failedEvents.get(0).metadata().get("probeType"));
            assertEquals("probe timeout", failedEvents.get(0).metadata().get("reason"));
        } finally {
            pm.stop(handle.id());
        }
    }

    // --- cleanup tests ---

    @Test
    void cleanupDelegatesToStop() throws Exception {
        CapturingEventBus bus = new CapturingEventBus();
        DefaultProcessManager pm = new DefaultProcessManager(bus);

        Process process = new ProcessBuilder("sleep", "60").start();
        BackgroundProcessHandle handle = pm.register(process, "sleep-tool", "sleep 60");

        boolean result = pm.cleanup(handle.id());
        assertTrue(result);

        assertEquals(ProcessState.STOPPED, pm.getState(handle.id()).orElse(null));
    }

    @Test
    void cleanupReturnsFalseForUnknownProcess() {
        CapturingEventBus bus = new CapturingEventBus();
        DefaultProcessManager pm = new DefaultProcessManager(bus);

        assertFalse(pm.cleanup("nonexistent"));
    }

    // --- Event emission tests ---

    @Test
    void serverToolReadyEventHasCorrectMetadata() throws Exception {
        CapturingEventBus bus = new CapturingEventBus();
        DefaultProcessManager pm = new DefaultProcessManager(bus);

        int port = startTcpListener(); // ServerSocket(0) binds synchronously; no sleep needed

        Process sleepProcess = new ProcessBuilder("sleep", "30").start();
        ReadyProbe probe = ReadyProbe.tcp(port);
        BackgroundProcessHandle handle = pm.registerWithProbe(sleepProcess, "my-server", "sleep 30", probe);
        try {
            pm.waitForReady(handle.id(), Duration.ofSeconds(5));

            Event event = bus.eventsOfType(EventType.SERVER_TOOL_READY).get(0);
            assertEquals(EventType.SERVER_TOOL_READY, event.type());
            assertEquals("my-server", event.source());
            assertEquals(handle.id(), event.metadata().get("processId"));
            assertEquals("my-server", event.metadata().get("toolName"));
            assertEquals("TCP", event.metadata().get("probeType"));
        } finally {
            pm.stop(handle.id());
        }
    }

    @Test
    void serverToolFailedEventHasCorrectMetadata() throws Exception {
        CapturingEventBus bus = new CapturingEventBus();
        DefaultProcessManager pm = new DefaultProcessManager(bus);

        ReadyProbe probe = new ReadyProbe(ProbeType.TCP, "localhost", 59999, null, 200,
                Duration.ofSeconds(1), Duration.ofMillis(100), 2);

        Process sleepProcess = new ProcessBuilder("sleep", "30").start();
        BackgroundProcessHandle handle = pm.registerWithProbe(sleepProcess, "fail-tool", "sleep 30", probe);
        try {
            pm.waitForReady(handle.id(), Duration.ofSeconds(2));

            Event event = bus.eventsOfType(EventType.SERVER_TOOL_FAILED).get(0);
            assertEquals(EventType.SERVER_TOOL_FAILED, event.type());
            assertEquals("fail-tool", event.source());
            assertEquals(handle.id(), event.metadata().get("processId"));
            assertEquals("TCP", event.metadata().get("probeType"));
            assertNotNull(event.metadata().get("reason"));
        } finally {
            pm.stop(handle.id());
        }
    }
}
