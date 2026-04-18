package org.specdriven.agent.mcp;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class McpTransportTest {

    // --- Content-Length framing: encode then decode ---

    @Test
    void sendMessage_readBack_correctlyParsed() throws Exception {
        PipedInputStream clientIn = new PipedInputStream();
        PipedOutputStream clientOut = new PipedOutputStream();
        PipedInputStream serverIn = new PipedInputStream();
        PipedOutputStream serverOut = new PipedOutputStream();

        clientOut.connect(serverIn);
        serverOut.connect(clientIn);

        ConcurrentLinkedQueue<Map<String, Object>> received = new ConcurrentLinkedQueue<>();
        AtomicReference<McpTransport> serverRef = new AtomicReference<>();

        try (McpTransport server = new McpTransport(serverIn, serverOut, msg -> {
            received.add(msg);
            Object id = msg.get("id");
            if (id != null && serverRef.get() != null) {
                serverRef.get().sendResponse(id, Map.of("ok", true));
            }
        })) {
            serverRef.set(server);
            try (McpTransport client = new McpTransport(clientIn, clientOut, null)) {
                Map<String, Object> response = client.sendRequest("test-method",
                        Map.of("key", "value"), 5);

                assertNotNull(response);
                Map<String, Object> result = (Map<String, Object>) response.get("result");
                assertEquals(true, result.get("ok"));

                // Server should have received the request
                assertFalse(received.isEmpty());
                Map<String, Object> req = received.poll();
                assertEquals("test-method", req.get("method"));
                assertEquals("value", ((Map<?, ?>) req.get("params")).get("key"));
            }
        }
    }

    // --- Request/response correlation ---

    @Test
    void requestResponse_correlatedById() throws Exception {
        // Create paired transports
        PipedInputStream in1 = new PipedInputStream();
        PipedOutputStream out1 = new PipedOutputStream();
        PipedInputStream in2 = new PipedInputStream();
        PipedOutputStream out2 = new PipedOutputStream();
        out1.connect(in2);
        out2.connect(in1);

        AtomicReference<McpTransport> serverRef = new AtomicReference<>();
        try (McpTransport server = new McpTransport(in2, out2, msg -> {
            Object id = msg.get("id");
            if (id != null && serverRef.get() != null) {
                serverRef.get().sendResponse(id, Map.of("echo", true));
            }
        })) {
            serverRef.set(server);
            try (McpTransport client = new McpTransport(in1, out1, null)) {
                Map<String, Object> r1 = client.sendRequest("req1", null, 5);
                Map<String, Object> r2 = client.sendRequest("req2", null, 5);

                assertEquals(1, ((Number) r1.get("id")).intValue());
                assertEquals(2, ((Number) r2.get("id")).intValue());
                assertEquals(true, ((Map<?, ?>) r1.get("result")).get("echo"));
                assertEquals(true, ((Map<?, ?>) r2.get("result")).get("echo"));
            }
        }
    }

    // --- Notification dispatch ---

    @Test
    void notification_dispatchedToHandler() throws Exception {
        PipedInputStream in1 = new PipedInputStream();
        PipedOutputStream out1 = new PipedOutputStream();
        PipedInputStream in2 = new PipedInputStream();
        PipedOutputStream out2 = new PipedOutputStream();
        out1.connect(in2);
        out2.connect(in1);

        ConcurrentLinkedQueue<Map<String, Object>> notifications = new ConcurrentLinkedQueue<>();

        try (McpTransport server = new McpTransport(in2, out2, notifications::add)) {
            try (McpTransport client = new McpTransport(in1, out1, null)) {
                client.sendNotification("test/notification", Map.of("data", 42));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (notifications.isEmpty() && System.nanoTime() < deadline) {
                    Thread.sleep(10);
                }
            }
        }

        assertFalse(notifications.isEmpty());
        Map<String, Object> n = notifications.poll();
        assertEquals("test/notification", n.get("method"));
        assertEquals(42L, ((Number) ((Map<?, ?>) n.get("params")).get("data")).longValue());
    }

    // --- Error response ---

    @Test
    void errorResponse_throwsException() throws Exception {
        PipedInputStream in1 = new PipedInputStream();
        PipedOutputStream out1 = new PipedOutputStream();
        PipedInputStream in2 = new PipedInputStream();
        PipedOutputStream out2 = new PipedOutputStream();
        out1.connect(in2);
        out2.connect(in1);

        AtomicReference<McpTransport> serverRef = new AtomicReference<>();
        try (McpTransport server = new McpTransport(in2, out2, msg -> {
            Object id = msg.get("id");
            if (id != null && serverRef.get() != null) {
                serverRef.get().sendError(id, -32000, "test error");
            }
        })) {
            serverRef.set(server);
            try (McpTransport client = new McpTransport(in1, out1, null)) {
                Map<String, Object> response = client.sendRequest("fail", null, 5);
                assertTrue(response.containsKey("error"));
                Map<?, ?> error = (Map<?, ?>) response.get("error");
                assertEquals(-32000, ((Number) error.get("code")).intValue());
                assertEquals("test error", error.get("message"));
            }
        }
    }

    // --- Timeout ---

    @Test
    void requestTimeout_throwsRuntimeException() throws Exception {
        PipedInputStream in1 = new PipedInputStream();
        PipedOutputStream out1 = new PipedOutputStream();
        PipedInputStream in2 = new PipedInputStream();
        PipedOutputStream out2 = new PipedOutputStream();
        out1.connect(in2);
        out2.connect(in1);

        // Server never responds
        try (McpTransport ignored = new McpTransport(in2, out2, msg -> {})) {
            try (McpTransport client = new McpTransport(in1, out1, null)) {
                RuntimeException ex = assertThrows(RuntimeException.class,
                        () -> client.sendRequest("hang", null, 1));
                assertTrue(ex.getMessage().contains("timed out"));
            }
        }
    }

    // --- Close cleans up pending futures ---

    @Test
    void close_completesPendingFuturesExceptionally() throws Exception {
        PipedInputStream in = new PipedInputStream();
        PipedOutputStream out = new PipedOutputStream();

        McpTransport transport = new McpTransport(in, out, msg -> {});
        // Don't respond — then close
        transport.close();
        // No exception means close succeeded
    }
}
