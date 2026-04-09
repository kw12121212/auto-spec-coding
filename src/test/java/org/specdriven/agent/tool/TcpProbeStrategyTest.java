package org.specdriven.agent.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.net.ServerSocket;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class TcpProbeStrategyTest {

    @Test
    void probeSucceedsWhenPortIsListening() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            ReadyProbe probe = ReadyProbe.tcp(port);
            TcpProbeStrategy strategy = new TcpProbeStrategy();
            assertTrue(strategy.probe(probe));
        }
    }

    @Test
    void probeFailsWhenPortIsNotListening() {
        // Use a port that is very unlikely to be in use
        ReadyProbe probe = new ReadyProbe(ProbeType.TCP, "localhost", 59999, null, 200,
                Duration.ofSeconds(1), Duration.ofMillis(100), 1);
        TcpProbeStrategy strategy = new TcpProbeStrategy();
        assertFalse(strategy.probe(probe));
    }

    @Test
    void probeFailsForInvalidHost() {
        ReadyProbe probe = new ReadyProbe(ProbeType.TCP, "nonexistent.invalid.host", 80, null, 200,
                Duration.ofSeconds(1), Duration.ofMillis(100), 1);
        TcpProbeStrategy strategy = new TcpProbeStrategy();
        assertFalse(strategy.probe(probe));
    }
}
