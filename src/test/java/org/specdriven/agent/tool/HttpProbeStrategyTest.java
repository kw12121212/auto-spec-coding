package org.specdriven.agent.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;

class HttpProbeStrategyTest {

    @Test
    void probeSucceedsOnExpectedStatus() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/health", exchange -> {
            String response = "OK";
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            ReadyProbe probe = ReadyProbe.http(port, "/health");
            HttpProbeStrategy strategy = new HttpProbeStrategy();
            assertTrue(strategy.probe(probe));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void probeFailsOnUnexpectedStatus() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/broken", exchange -> {
            exchange.sendResponseHeaders(503, 0);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            ReadyProbe probe = ReadyProbe.http(port, "/broken");
            HttpProbeStrategy strategy = new HttpProbeStrategy();
            assertFalse(strategy.probe(probe));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void probeFailsWhenServerNotRunning() {
        ReadyProbe probe = new ReadyProbe(ProbeType.HTTP, "localhost", 59999, "/", 200,
                Duration.ofSeconds(1), Duration.ofMillis(100), 1);
        HttpProbeStrategy strategy = new HttpProbeStrategy();
        assertFalse(strategy.probe(probe));
    }

    @Test
    void probeSucceedsWithCustomExpectedStatus() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/created", exchange -> {
            exchange.sendResponseHeaders(201, 0);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            ReadyProbe probe = new ReadyProbe(ProbeType.HTTP, "localhost", port, "/created", 201,
                    Duration.ofSeconds(5), Duration.ofMillis(200), 10);
            HttpProbeStrategy strategy = new HttpProbeStrategy();
            assertTrue(strategy.probe(probe));
        } finally {
            server.stop(0);
        }
    }
}
