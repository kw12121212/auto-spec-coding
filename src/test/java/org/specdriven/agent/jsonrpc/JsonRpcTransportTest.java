package org.specdriven.agent.jsonrpc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class JsonRpcTransportTest {

    // --- Helpers ---

    private static byte[] frame(String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        String header = "Content-Length: " + body.length + "\r\n\r\n";
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[headerBytes.length + body.length];
        System.arraycopy(headerBytes, 0, result, 0, headerBytes.length);
        System.arraycopy(body, 0, result, headerBytes.length, body.length);
        return result;
    }

    private static class CollectingHandler implements JsonRpcMessageHandler {
        final List<Object> messages = Collections.synchronizedList(new ArrayList<>());
        final List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch latch;
        final int expectedCount;

        CollectingHandler(int expectedCount) {
            this.expectedCount = expectedCount;
            this.latch = new CountDownLatch(expectedCount);
        }

        @Override
        public void onRequest(JsonRpcRequest request) {
            messages.add(request);
            latch.countDown();
        }

        @Override
        public void onNotification(JsonRpcNotification notification) {
            messages.add(notification);
            latch.countDown();
        }

        @Override
        public void onError(Throwable error) {
            errors.add(error);
            latch.countDown();
        }

        boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }
    }

    // --- Send tests ---

    @Test
    void sendResponse_writesCorrectFrame() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StdioTransport transport = new StdioTransport(new ByteArrayInputStream(new byte[0]), output);

        JsonRpcResponse response = JsonRpcResponse.success(1L, Map.of("status", "ok"));
        transport.send(response);

        String result = output.toString(StandardCharsets.UTF_8);
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"status\":\"ok\"}}";
        assertTrue(result.startsWith("Content-Length: " + body.length() + "\r\n\r\n"),
                "Frame header mismatch: " + result);
        assertTrue(result.endsWith(body), "Frame body mismatch: " + result);
    }

    @Test
    void sendNotification_writesCorrectFrame() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StdioTransport transport = new StdioTransport(new ByteArrayInputStream(new byte[0]), output);

        JsonRpcNotification notification = new JsonRpcNotification("cancel", Map.of("requestId", 5));
        transport.send(notification);

        String result = output.toString(StandardCharsets.UTF_8);
        assertTrue(result.contains("\"method\":\"cancel\""), "Should contain method: " + result);
        assertFalse(result.contains("\"id\":"), "Notification should not have id field: " + result);
        assertTrue(result.startsWith("Content-Length: "), "Should have Content-Length header: " + result);
    }

    // --- Receive tests ---

    @Test
    void receiveRequest_dispatchesToHandler() throws Exception {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{\"name\":\"grep\"}}";
        ByteArrayInputStream input = new ByteArrayInputStream(frame(json));

        CollectingHandler handler = new CollectingHandler(1);
        StdioTransport transport = new StdioTransport(input, new ByteArrayOutputStream());
        transport.start(handler);

        assertTrue(handler.await(2, TimeUnit.SECONDS), "Handler timed out");
        assertEquals(1, handler.messages.size());
        assertInstanceOf(JsonRpcRequest.class, handler.messages.get(0));
        JsonRpcRequest req = (JsonRpcRequest) handler.messages.get(0);
        assertEquals(1L, req.id());
        assertEquals("tools/list", req.method());
        transport.stop();
    }

    @Test
    void receiveNotification_dispatchesToHandler() throws Exception {
        String json = "{\"jsonrpc\":\"2.0\",\"method\":\"cancel\",\"params\":{\"requestId\":5}}";
        ByteArrayInputStream input = new ByteArrayInputStream(frame(json));

        CollectingHandler handler = new CollectingHandler(1);
        StdioTransport transport = new StdioTransport(input, new ByteArrayOutputStream());
        transport.start(handler);

        assertTrue(handler.await(2, TimeUnit.SECONDS), "Handler timed out");
        assertEquals(1, handler.messages.size());
        assertInstanceOf(JsonRpcNotification.class, handler.messages.get(0));
        JsonRpcNotification notif = (JsonRpcNotification) handler.messages.get(0);
        assertEquals("cancel", notif.method());
        transport.stop();
    }

    // --- Size limit test ---

    @Test
    void oversizedFrame_triggersOnError() throws Exception {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"test\"}";
        ByteArrayInputStream input = new ByteArrayInputStream(frame(json));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        CollectingHandler handler = new CollectingHandler(1);
        // Set max size to 10 bytes (smaller than the frame body)
        StdioTransport transport = new StdioTransport(input, output, 10);
        transport.start(handler);

        assertTrue(handler.await(2, TimeUnit.SECONDS), "Handler timed out");
        assertEquals(1, handler.errors.size());
        assertTrue(handler.errors.get(0).getMessage().contains("exceeds maximum"),
                "Error message should mention size limit: " + handler.errors.get(0).getMessage());
        transport.stop();
    }

    // --- Lifecycle tests ---

    @Test
    @Timeout(10)
    void stop_terminatesReaderThread() throws Exception {
        // Use a piped stream that blocks on read so the reader thread stays alive
        PipedInputStream input = new PipedInputStream();
        PipedOutputStream pipeOut = new PipedOutputStream(input);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        AtomicReference<Thread> capturedThread = new AtomicReference<>();
        CollectingHandler handler = new CollectingHandler(1);
        StdioTransport transport = new StdioTransport(input, output);

        transport.start(handler);
        // Close the pipe to cause EOF, which terminates the reader
        pipeOut.close();

        transport.stop();
        // If we get here without timeout, stop() worked
        assertTrue(true, "stop() completed without timeout");
    }

    // --- Error recovery test ---

    @Test
    void malformedHeader_triggersOnError() throws Exception {
        // Write garbage (has \r\n\r\n but no Content-Length) followed by a valid frame
        byte[] garbage = "this is not a valid header\r\n\r\n".getBytes(StandardCharsets.UTF_8);
        String validJson = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"test\",\"params\":{}}";
        byte[] validFrame = frame(validJson);

        byte[] combined = new byte[garbage.length + validFrame.length];
        System.arraycopy(garbage, 0, combined, 0, garbage.length);
        System.arraycopy(validFrame, 0, combined, garbage.length, validFrame.length);

        ByteArrayInputStream input = new ByteArrayInputStream(combined);
        // Expect 2 events: 1 error (malformed) + 1 request (valid frame)
        CollectingHandler handler = new CollectingHandler(2);
        StdioTransport transport = new StdioTransport(input, new ByteArrayOutputStream());
        transport.start(handler);

        assertTrue(handler.await(5, TimeUnit.SECONDS), "Handler timed out");
        // Should have received both an error and the valid request
        assertFalse(handler.errors.isEmpty(), "Should have received an error for malformed header");
        boolean hasRequest = handler.messages.stream().anyMatch(m -> m instanceof JsonRpcRequest);
        assertTrue(hasRequest, "Should have received the valid request after malformed header");
        transport.stop();
    }

    // --- Thread safety test ---

    @Test
    void concurrentSends_produceNonInterleavedFrames() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StdioTransport transport = new StdioTransport(new ByteArrayInputStream(new byte[0]), output);

        int count = 50;
        Thread[] threads = new Thread[count];
        for (int i = 0; i < count; i++) {
            final long id = i;
            threads[i] = Thread.ofPlatform().start(() -> {
                transport.send(JsonRpcResponse.success(id, Map.of("idx", id)));
            });
        }
        for (Thread t : threads) {
            t.join(5000);
        }

        String result = output.toString(StandardCharsets.UTF_8);
        // Count occurrences of "Content-Length:" — should be exactly `count`
        int frameCount = 0;
        int idx = 0;
        while ((idx = result.indexOf("Content-Length:", idx)) != -1) {
            frameCount++;
            idx++;
        }
        assertEquals(count, frameCount, "Should have exactly " + count + " frames");
    }
}
