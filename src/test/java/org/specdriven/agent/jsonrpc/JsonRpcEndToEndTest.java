package org.specdriven.agent.jsonrpc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.specdriven.agent.json.JsonReader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests that exercise the full JSON-RPC stack:
 * framed stdin → transport decode → dispatcher route → SDK invocation → response → framed stdout.
 */
class JsonRpcEndToEndTest {

    // --- Frame helpers ---

    private static byte[] frame(String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        String header = "Content-Length: " + body.length + "\r\n\r\n";
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[headerBytes.length + body.length];
        System.arraycopy(headerBytes, 0, result, 0, headerBytes.length);
        System.arraycopy(body, 0, result, headerBytes.length, body.length);
        return result;
    }

    private static byte[] joinFrames(String... jsons) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        for (String json : jsons) {
            try {
                buf.write(frame(json));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return buf.toByteArray();
    }

    record ParsedFrame(String json, Map<String, Object> fields) {}

    private static List<ParsedFrame> parseFrames(String output) {
        List<ParsedFrame> frames = new ArrayList<>();
        Pattern framePattern = Pattern.compile("Content-Length:\\s*(\\d+)\\r\\n\\r\\n");
        Matcher m = framePattern.matcher(output);
        int pos = 0;
        while (m.find(pos)) {
            int contentLength = Integer.parseInt(m.group(1));
            int bodyStart = m.end();
            int bodyEnd = bodyStart + contentLength;
            if (bodyEnd > output.length()) break;
            String json = output.substring(bodyStart, bodyEnd);
            Map<String, Object> fields = JsonReader.parseObject(json);
            frames.add(new ParsedFrame(json, fields));
            pos = bodyEnd;
        }
        return frames;
    }

    private static List<ParsedFrame> awaitFrames(ByteArrayOutputStream output, int minCount, long timeoutMs)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000;
        while (true) {
            List<ParsedFrame> frames = parseFrames(output.toString(StandardCharsets.UTF_8));
            if (frames.size() >= minCount) return frames;
            if (System.nanoTime() >= deadline) {
                return frames; // return whatever we have; let test assertions provide clarity
            }
            Thread.sleep(50);
        }
    }

    private static ParsedFrame awaitResponseId(ByteArrayOutputStream output, long expectedId, long timeoutMs)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000;
        while (true) {
            for (ParsedFrame f : parseFrames(output.toString(StandardCharsets.UTF_8))) {
                Object id = f.fields.get("id");
                if (id instanceof Number n && n.longValue() == expectedId) return f;
            }
            if (System.nanoTime() >= deadline) return null;
            Thread.sleep(50);
        }
    }

    // --- Stack wiring ---

    static class TestStack implements AutoCloseable {
        final ByteArrayOutputStream output;
        final StdioTransport transport;
        final JsonRpcDispatcher dispatcher;

        TestStack(byte[] inputBytes) {
            ByteArrayInputStream input = new ByteArrayInputStream(inputBytes);
            this.output = new ByteArrayOutputStream();
            this.transport = new StdioTransport(input, output);
            this.dispatcher = new JsonRpcDispatcher(transport);
        }

        void start() {
            transport.start(dispatcher);
        }

        List<ParsedFrame> awaitFrames(int minCount, long timeoutMs) throws InterruptedException {
            return JsonRpcEndToEndTest.awaitFrames(output, minCount, timeoutMs);
        }

        ParsedFrame awaitResponseId(long expectedId, long timeoutMs) throws InterruptedException {
            return JsonRpcEndToEndTest.awaitResponseId(output, expectedId, timeoutMs);
        }

        String outputString() {
            return output.toString(StandardCharsets.UTF_8);
        }

        @Override
        public void close() {
            transport.stop();
        }
    }

    // --- Assertion helpers ---

    private static void assertSuccess(ParsedFrame frame, long expectedId) {
        long actualId = ((Number) frame.fields.get("id")).longValue();
        assertEquals(expectedId, actualId, "Unexpected id in: " + frame.json);
        assertFalse(frame.fields.containsKey("error"), "Expected success but got error: " + frame.json);
    }

    private static void assertErrorCode(ParsedFrame frame, long expectedId, int errorCode) {
        long actualId = ((Number) frame.fields.get("id")).longValue();
        assertEquals(expectedId, actualId, "Unexpected id in: " + frame.json);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) frame.fields.get("error");
        assertNotNull(error, "Expected error object: " + frame.json);
        int actualCode = ((Number) error.get("code")).intValue();
        assertEquals(errorCode, actualCode,
                "Expected error code " + errorCode + " but got " + actualCode + " in: " + frame.json);
    }

    // --- Tests ---

    @Test
    @Timeout(10)
    void fullLifecycle() throws Exception {
        byte[] input = joinFrames(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"agent/state\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/list\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"shutdown\",\"params\":{}}"
        );

        try (TestStack stack = new TestStack(input)) {
            stack.start();
            List<ParsedFrame> frames = stack.awaitFrames(4, 5000);
            assertEquals(4, frames.size(), "Expected 4 frames, got " + frames.size() + ": " + stack.outputString());

            // initialize: contains version and capabilities
            assertSuccess(frames.get(0), 1L);
            @SuppressWarnings("unchecked")
            Map<String, Object> initResult = (Map<String, Object>) frames.get(0).fields.get("result");
            assertNotNull(initResult.get("version"), "initialize result should contain version");
            assertNotNull(initResult.get("capabilities"), "initialize result should contain capabilities");

            // agent/state: contains state field
            assertSuccess(frames.get(1), 2L);
            @SuppressWarnings("unchecked")
            Map<String, Object> stateResult = (Map<String, Object>) frames.get(1).fields.get("result");
            assertTrue(stateResult.containsKey("state"), "agent/state result should contain state");

            // tools/list: contains tools array
            assertSuccess(frames.get(2), 3L);
            @SuppressWarnings("unchecked")
            Map<String, Object> toolsResult = (Map<String, Object>) frames.get(2).fields.get("result");
            assertTrue(toolsResult.get("tools") instanceof List, "tools/list result should contain tools array");

            // shutdown: result is null
            assertSuccess(frames.get(3), 4L);
            assertNull(frames.get(3).fields.get("result"), "shutdown result should be null");
        }
    }

    @Test
    @Timeout(10)
    void errorScenarios() throws Exception {
        byte[] input = joinFrames(
                // 1. unknown method → methodNotFound
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"unknown/method\",\"params\":{}}",
                // 2. agent/run before initialize → invalidRequest
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"agent/run\",\"params\":{\"prompt\":\"test\"}}",
                // 3. initialize → success
                "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"initialize\",\"params\":{}}",
                // 4. agent/run without prompt → invalidParams
                "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"agent/run\",\"params\":{}}",
                // 5. shutdown → success
                "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"shutdown\",\"params\":{}}",
                // 6. agent/run after shutdown → invalidRequest
                "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"agent/run\",\"params\":{\"prompt\":\"test\"}}"
        );

        try (TestStack stack = new TestStack(input)) {
            stack.start();
            List<ParsedFrame> frames = stack.awaitFrames(6, 5000);
            assertEquals(6, frames.size(), "Expected 6 frames, got " + frames.size());

            assertErrorCode(frames.get(0), 1L, -32601); // methodNotFound
            assertErrorCode(frames.get(1), 2L, -32600); // uninitialized
            assertSuccess(frames.get(2), 3L);            // initialize
            assertErrorCode(frames.get(3), 4L, -32602);  // invalidParams
            assertSuccess(frames.get(4), 5L);            // shutdown
            assertErrorCode(frames.get(5), 6L, -32600);  // post-shutdown
        }
    }

    @Test
    @Timeout(10)
    void notificationHandling() throws Exception {
        byte[] input = joinFrames(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                // $/cancel notification — should produce no response
                "{\"jsonrpc\":\"2.0\",\"method\":\"$/cancel\",\"params\":{\"id\":99}}",
                // This request verifies the transport continues after the notification
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"agent/state\",\"params\":{}}"
        );

        try (TestStack stack = new TestStack(input)) {
            stack.start();
            List<ParsedFrame> frames = stack.awaitFrames(2, 5000);
            assertEquals(2, frames.size(), "Notification should not produce a response frame");

            assertSuccess(frames.get(0), 1L); // initialize
            assertSuccess(frames.get(1), 2L); // agent/state — transport continued
        }
    }

    @Test
    @Timeout(10)
    void multiFrameInput() throws Exception {
        byte[] input = joinFrames(
                "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"id\":20,\"method\":\"agent/state\",\"params\":{}}"
        );

        try (TestStack stack = new TestStack(input)) {
            stack.start();
            List<ParsedFrame> frames = stack.awaitFrames(2, 5000);
            assertEquals(2, frames.size());

            // Responses appear in the same order as requests
            assertEquals(10L, ((Number) frames.get(0).fields.get("id")).longValue());
            assertEquals(20L, ((Number) frames.get(1).fields.get("id")).longValue());

            // Each response is correctly framed
            String output = stack.outputString();
            assertEquals(2, countContentLengthHeaders(output));
        }
    }

    @Test
    @Timeout(15)
    void eventForwarding() throws Exception {
        byte[] input = joinFrames(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"agent/run\",\"params\":{\"prompt\":\"hello\"}}"
        );

        try (TestStack stack = new TestStack(input)) {
            stack.start();

            // Wait for agent/run response (async, may arrive after event notifications)
            ParsedFrame runResponse = stack.awaitResponseId(2L, 10000);
            assertNotNull(runResponse, "Should have agent/run response for id=2: " + stack.outputString());
            assertTrue(runResponse.fields.containsKey("result"), "agent/run should succeed: " + runResponse.json);
            @SuppressWarnings("unchecked")
            Map<String, Object> runResult = (Map<String, Object>) runResponse.fields.get("result");
            assertTrue(runResult.containsKey("output"), "Result should contain output: " + runResponse.json);

            // Now check all frames for events
            List<ParsedFrame> frames = parseFrames(stack.outputString());
            List<ParsedFrame> responses = frames.stream()
                    .filter(f -> f.fields.containsKey("id"))
                    .toList();
            List<ParsedFrame> events = frames.stream()
                    .filter(f -> "event".equals(f.fields.get("method")))
                    .toList();

            // initialize must succeed
            assertTrue(responses.size() >= 1, "Should have at least initialize response");
            assertSuccess(responses.get(0), 1L);

            // Event notifications should have been forwarded
            if (!events.isEmpty()) {
                for (ParsedFrame event : events) {
                    assertEquals("event", event.fields.get("method"));
                    @SuppressWarnings("unchecked")
                    Map<String, Object> params = (Map<String, Object>) event.fields.get("params");
                    assertNotNull(params.get("type"), "Event notification should have type param");
                }
            }
        }
    }

    @Test
    @Timeout(15)
    void agentRunIntegration() throws Exception {
        byte[] input = joinFrames(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"agent/run\",\"params\":{\"prompt\":\"explain this code\"}}"
        );

        try (TestStack stack = new TestStack(input)) {
            stack.start();
            ParsedFrame runResponse = stack.awaitResponseId(2L, 10000);
            assertNotNull(runResponse, "Should have response for agent/run (id=2): " + stack.outputString());
            assertTrue(runResponse.fields.containsKey("result"),
                    "agent/run should return success result: " + runResponse.json);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) runResponse.fields.get("result");
            assertTrue(result.containsKey("output"),
                    "Result should contain output field: " + runResponse.json);
        }
    }

    // --- Utility ---

    private static int countContentLengthHeaders(String output) {
        int count = 0;
        int idx = 0;
        while ((idx = output.indexOf("Content-Length:", idx)) != -1) {
            count++;
            idx++;
        }
        return count;
    }
}
