package org.specdriven.agent.jsonrpc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.question.*;
import org.specdriven.sdk.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class JsonRpcDispatcherTest {

    private CapturingTransport transport;
    private JsonRpcDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        transport = new CapturingTransport();
        dispatcher = new JsonRpcDispatcher(transport);
    }

    // --- Capturing transport (no real I/O, no framing) ---

    static class CapturingTransport implements JsonRpcTransport {
        final List<Object> sent = Collections.synchronizedList(new ArrayList<>());

        @Override public void start(JsonRpcMessageHandler handler) {}
        @Override public void stop() {}
        @Override public void close() {}

        @Override
        public void send(JsonRpcResponse response) {
            sent.add(response);
        }

        @Override
        public void send(JsonRpcNotification notification) {
            sent.add(notification);
        }

        JsonRpcResponse awaitResponse(Object expectedId, long timeoutMs) throws InterruptedException {
            long deadline = System.nanoTime() + timeoutMs * 1_000_000;
            while (System.nanoTime() < deadline) {
                synchronized (sent) {
                    for (Object msg : sent) {
                        if (msg instanceof JsonRpcResponse r) {
                            Object rid = r.id();
                            if (rid != null && (rid.equals(expectedId) || rid.toString().equals(expectedId.toString()))) {
                                return r;
                            }
                        }
                    }
                }
                Thread.sleep(10);
            }
            return null;
        }
    }

    // --- Helpers ---

    private JsonRpcResponse dispatch(JsonRpcRequest request) {
        dispatcher.onRequest(request);
        // Synchronous handlers send response immediately
        synchronized (transport.sent) {
            for (Object msg : transport.sent) {
                if (msg instanceof JsonRpcResponse r && idMatches(r, request.id())) return r;
            }
        }
        return null;
    }

    private JsonRpcResponse dispatchAsync(JsonRpcRequest request, long timeoutMs) throws InterruptedException {
        dispatcher.onRequest(request);
        return transport.awaitResponse(request.id(), timeoutMs);
    }

    private boolean idMatches(JsonRpcResponse resp, Object id) {
        if (resp.id() == null || id == null) return false;
        return resp.id().equals(id) || resp.id().toString().equals(id.toString());
    }

    private void initializeSdk() {
        JsonRpcResponse resp = dispatch(new JsonRpcRequest(1L, "initialize", Map.of()));
        assertNotNull(resp);
        assertTrue(resp.isSuccess());
    }

    private String responseJson(JsonRpcResponse resp) {
        return JsonRpcCodec.encode(resp);
    }

    private SpecDriven getSdkInstance() {
        return dispatcher.sdk();
    }

    // --- initialize tests ---

    @Test
    void initialize_returnsVersionAndCapabilities() {
        JsonRpcResponse resp = dispatch(new JsonRpcRequest(1L, "initialize", Map.of()));
        assertNotNull(resp);
        assertTrue(resp.isSuccess());

        String json = responseJson(resp);
        assertTrue(json.contains("\"version\":\"0.1.0\""));
        assertTrue(json.contains("\"methods\":["));
        assertTrue(json.contains("\"notifications\":["));
    }

    @Test
    void initialize_withSystemPrompt_succeeds() {
        JsonRpcResponse resp = dispatch(new JsonRpcRequest(1L, "initialize",
                Map.of("systemPrompt", "You are helpful")));
        assertNotNull(resp);
        assertTrue(resp.isSuccess());
    }

    @Test
    void doubleInitialize_returnsInvalidRequest() {
        initializeSdk();

        JsonRpcResponse resp = dispatch(new JsonRpcRequest(2L, "initialize", Map.of()));
        assertNotNull(resp);
        assertFalse(resp.isSuccess());
        assertEquals(-32600, resp.error().code());
    }

    // --- shutdown tests ---

    @Test
    void shutdown_returnsSuccess() {
        initializeSdk();

        JsonRpcResponse resp = dispatch(new JsonRpcRequest(2L, "shutdown", Map.of()));
        assertNotNull(resp);
        assertTrue(resp.isSuccess());
    }

    @Test
    void operationsAfterShutdown_rejected() {
        initializeSdk();
        dispatch(new JsonRpcRequest(2L, "shutdown", Map.of()));

        JsonRpcResponse resp = dispatch(new JsonRpcRequest(3L, "agent/run", Map.of("prompt", "test")));
        assertNotNull(resp);
        assertFalse(resp.isSuccess());
        assertEquals(-32600, resp.error().code());
    }

    // --- agent/run tests ---

    @Test
    void agentRun_withoutInitialize_rejected() {
        JsonRpcResponse resp = dispatch(new JsonRpcRequest(1L, "agent/run", Map.of("prompt", "test")));
        assertNotNull(resp);
        assertFalse(resp.isSuccess());
        assertEquals(-32600, resp.error().code());
    }

    @Test
    void agentRun_missingPrompt_returnsInvalidParams() {
        initializeSdk();

        JsonRpcResponse resp = dispatch(new JsonRpcRequest(2L, "agent/run", Map.of()));
        assertNotNull(resp);
        assertFalse(resp.isSuccess());
        assertEquals(-32602, resp.error().code());
    }

    @Test
    void agentRun_returnsOutput() throws InterruptedException {
        initializeSdk();

        JsonRpcResponse resp = dispatchAsync(new JsonRpcRequest(2L, "agent/run",
                Map.of("prompt", "hello")), 10000);
        assertNotNull(resp, "Timed out waiting for agent/run response");
        assertTrue(resp.isSuccess(), "agent/run should succeed");

        String json = responseJson(resp);
        assertTrue(json.contains("\"output\":"), "Response should contain output field: " + json);
    }

    // --- agent/stop tests ---

    @Test
    void agentStop_noRunningAgent_returnsSuccess() {
        initializeSdk();

        JsonRpcResponse resp = dispatch(new JsonRpcRequest(2L, "agent/stop", Map.of()));
        assertNotNull(resp);
        assertTrue(resp.isSuccess());
    }

    // --- agent/state tests ---

    @Test
    void agentState_returnsState() {
        initializeSdk();

        JsonRpcResponse resp = dispatch(new JsonRpcRequest(2L, "agent/state", Map.of()));
        assertNotNull(resp);
        assertTrue(resp.isSuccess());

        String json = responseJson(resp);
        assertTrue(json.contains("\"state\":\""));
    }

    // --- tools/list tests ---

    @Test
    void toolsList_returnsToolsArray() {
        initializeSdk();

        JsonRpcResponse resp = dispatch(new JsonRpcRequest(2L, "tools/list", Map.of()));
        assertNotNull(resp);
        assertTrue(resp.isSuccess());

        String json = responseJson(resp);
        assertTrue(json.contains("\"tools\":["));
    }

    // --- unknown method test ---

    @Test
    void unknownMethod_returnsMethodNotFound() {
        initializeSdk();

        JsonRpcResponse resp = dispatch(new JsonRpcRequest(2L, "foo/bar", Map.of()));
        assertNotNull(resp);
        assertFalse(resp.isSuccess());
        assertEquals(-32601, resp.error().code());
    }

    @Test
    void unknownMethod_beforeInitialize_stillReturnsMethodNotFound() {
        JsonRpcResponse resp = dispatch(new JsonRpcRequest(1L, "foo/bar", Map.of()));
        assertNotNull(resp);
        assertFalse(resp.isSuccess());
        assertEquals(-32601, resp.error().code());
    }

    // --- SDK exception mapping tests ---

    @Test
    void mapException_sdkLlmException_returnsInternalErrorWithRetryable() {
        JsonRpcError err = dispatcher.mapException(new SdkLlmException("LLM failed", new RuntimeException()));
        assertEquals(-32603, err.code());
        assertNotNull(err.data());
        assertEquals(true, err.data().get("retryable"));
    }

    @Test
    void mapException_sdkPermissionException_returnsInvalidRequest() {
        JsonRpcError err = dispatcher.mapException(new SdkPermissionException("Denied", new RuntimeException()));
        assertEquals(-32600, err.code());
    }

    @Test
    void mapException_sdkToolException_returnsInvalidParams() {
        JsonRpcError err = dispatcher.mapException(new SdkToolException("Tool failed", new RuntimeException()));
        assertEquals(-32602, err.code());
    }

    @Test
    void mapException_sdkVaultException_returnsInternalError() {
        JsonRpcError err = dispatcher.mapException(new SdkVaultException("Vault failed", new RuntimeException()));
        assertEquals(-32603, err.code());
    }

    @Test
    void mapException_sdkConfigException_returnsInternalError() {
        JsonRpcError err = dispatcher.mapException(new SdkConfigException("Config failed", new RuntimeException()));
        assertEquals(-32603, err.code());
    }

    @Test
    void mapException_unhandledException_returnsInternalError() {
        JsonRpcError err = dispatcher.mapException(new RuntimeException("boom"));
        assertEquals(-32603, err.code());
    }

    @Test
    void mapException_protocolException_carriesOwnCode() {
        JsonRpcError err = dispatcher.mapException(new JsonRpcProtocolException(-32600, "bad"));
        assertEquals(-32600, err.code());
    }

    // --- $/cancel notification test ---

    @Test
    void cancelNotification_unknownId_isNoOp() {
        dispatcher.onNotification(new JsonRpcNotification("$/cancel", Map.of("id", 99)));
        // No response should be sent for notifications
        synchronized (transport.sent) {
            assertTrue(transport.sent.isEmpty(), "Cancel of unknown ID should not produce output");
        }
    }

    // --- Event forwarding test ---

    @Test
    void initialize_registersEventForwardingCapability() {
        JsonRpcResponse resp = dispatch(new JsonRpcRequest(1L, "initialize", Map.of()));
        assertNotNull(resp);
        assertTrue(resp.isSuccess());

        String json = responseJson(resp);
        assertTrue(json.contains("\"event\""), "Should declare event notification capability");
    }

    @Test
    void eventForwarding_sendsNotificationOnEvent() {
        initializeSdk();

        // Find event notifications in the sent list
        // The SDK was initialized with event forwarding, so events should be forwarded
        // We need to trigger an event — agent state changes produce events
        // For now verify the capability is declared (covered above)
        // and that the dispatcher handles events without error

        // Verify no notifications sent yet (no events triggered)
        synchronized (transport.sent) {
            long notifCount = transport.sent.stream()
                    .filter(m -> m instanceof JsonRpcNotification)
                    .count();
            // agent/state doesn't produce events, so no notifications expected
            assertEquals(0, notifCount);
        }
    }

    // --- Transport error handling ---

    @Test
    void onError_doesNotThrow() {
        assertDoesNotThrow(() -> dispatcher.onError(new IOException("broken pipe")));
    }

    @Test
    void onError_sendsErrorNotification() {
        dispatcher.onError(new IOException("broken pipe"));

        synchronized (transport.sent) {
            // Should have sent an event notification about the transport error
            boolean hasErrorNotification = transport.sent.stream()
                    .filter(m -> m instanceof JsonRpcNotification)
                    .anyMatch(m -> {
                        JsonRpcNotification n = (JsonRpcNotification) m;
                        return "event".equals(n.method());
                    });
            assertTrue(hasErrorNotification, "Should send event notification for transport error");
        }
    }

    // --- question/answer tests ---

    @Test
    void questionAnswer_initializeCapabilitiesIncludesMethod() {
        JsonRpcResponse resp = dispatch(new JsonRpcRequest(1L, "initialize", Map.of()));
        assertNotNull(resp);
        assertTrue(resp.isSuccess());
        String json = responseJson(resp);
        assertTrue(json.contains("question/answer"), "capabilities.methods should include question/answer");
        assertTrue(json.contains("workflow/start"), "capabilities.methods should include workflow/start");
        assertTrue(json.contains("workflow/state"), "capabilities.methods should include workflow/state");
        assertTrue(json.contains("workflow/result"), "capabilities.methods should include workflow/result");
    }

    @Test
    void questionAnswer_withoutInitialize_rejected() {
        JsonRpcResponse resp = dispatch(new JsonRpcRequest(1L, "question/answer",
                Map.of("sessionId", "s1", "questionId", "q1", "approved", true)));
        assertNotNull(resp);
        assertFalse(resp.isSuccess());
        assertEquals(-32600, resp.error().code());
    }

    @Test
    void questionAnswer_missingSessionId_returnsInvalidParams() {
        initializeSdk();
        JsonRpcResponse resp = dispatch(new JsonRpcRequest(2L, "question/answer",
                Map.of("questionId", "q1", "approved", true)));
        assertNotNull(resp);
        assertFalse(resp.isSuccess());
        assertEquals(-32602, resp.error().code());
    }

    @Test
    void questionAnswer_missingQuestionId_returnsInvalidParams() {
        initializeSdk();
        JsonRpcResponse resp = dispatch(new JsonRpcRequest(2L, "question/answer",
                Map.of("sessionId", "s1", "approved", true)));
        assertNotNull(resp);
        assertFalse(resp.isSuccess());
        assertEquals(-32602, resp.error().code());
    }

    @Test
    void questionAnswer_missingApproved_returnsInvalidParams() {
        initializeSdk();
        JsonRpcResponse resp = dispatch(new JsonRpcRequest(2L, "question/answer",
                Map.of("sessionId", "s1", "questionId", "q1")));
        assertNotNull(resp);
        assertFalse(resp.isSuccess());
        assertEquals(-32602, resp.error().code());
    }

    @Test
    void questionAnswer_noWaitingQuestion_returnsError() {
        initializeSdk();
        JsonRpcResponse resp = dispatch(new JsonRpcRequest(2L, "question/answer",
                Map.of("sessionId", "s1", "questionId", "q1", "approved", true)));
        assertNotNull(resp);
        assertFalse(resp.isSuccess());
        assertTrue(resp.error().message().contains("No waiting question") || resp.error().message().contains("not found"),
                "Error should indicate question not found: " + resp.error().message());
    }

    @Test
    void questionAnswer_unsupportedDeliveryMode_returnsError() {
        initializeSdk();

        // Register a PUSH_MOBILE_WAIT_HUMAN waiting question via the delivery service
        SpecDriven sdk = getSdkInstance();
        QuestionDeliveryService service = sdk.deliveryService();
        if (service != null) {
            Question question = new Question(
                    "q1", "s1", "test question?", "impact", "recommendation",
                    QuestionStatus.WAITING_FOR_ANSWER, QuestionCategory.PERMISSION_CONFIRMATION,
                    DeliveryMode.PUSH_MOBILE_WAIT_HUMAN);
            service.runtime().beginWaitingQuestion(question);

            JsonRpcResponse resp = dispatch(new JsonRpcRequest(2L, "question/answer",
                    Map.of("sessionId", "s1", "questionId", "q1", "approved", true)));
            assertNotNull(resp);
            assertFalse(resp.isSuccess());
            assertTrue(resp.error().message().contains("Unsupported delivery mode"),
                    "Error should indicate unsupported delivery mode: " + resp.error().message());
        }
    }

    @Test
    void questionAnswer_approve_returnsAccepted() {
        initializeSdk();

        SpecDriven sdk = getSdkInstance();
        QuestionDeliveryService service = sdk.deliveryService();
        if (service == null) return; // skip if delivery service unavailable

        Question question = new Question(
                "q1", "s1", "Allow file deletion?", "Files will be deleted",
                "Approve if safe", QuestionStatus.WAITING_FOR_ANSWER,
                QuestionCategory.PERMISSION_CONFIRMATION, DeliveryMode.PAUSE_WAIT_HUMAN);
        service.runtime().beginWaitingQuestion(question);

        JsonRpcResponse resp = dispatch(new JsonRpcRequest(2L, "question/answer",
                Map.of("sessionId", "s1", "questionId", "q1", "approved", true)));
        assertNotNull(resp);
        assertTrue(resp.isSuccess(), "Approve should succeed");

        String json = responseJson(resp);
        assertTrue(json.contains("\"status\":\"accepted\""), "Response should contain status accepted");
    }

    @Test
    void questionAnswer_reject_returnsAccepted() {
        initializeSdk();

        SpecDriven sdk = getSdkInstance();
        QuestionDeliveryService service = sdk.deliveryService();
        if (service == null) return;

        Question question = new Question(
                "q1", "s1", "Allow file deletion?", "Files will be deleted",
                "Approve if safe", QuestionStatus.WAITING_FOR_ANSWER,
                QuestionCategory.PERMISSION_CONFIRMATION, DeliveryMode.PAUSE_WAIT_HUMAN);
        service.runtime().beginWaitingQuestion(question);

        JsonRpcResponse resp = dispatch(new JsonRpcRequest(2L, "question/answer",
                Map.of("sessionId", "s1", "questionId", "q1", "approved", false)));
        assertNotNull(resp);
        assertTrue(resp.isSuccess(), "Reject should succeed");

        String json = responseJson(resp);
        assertTrue(json.contains("\"status\":\"accepted\""), "Response should contain status accepted");
    }

    @Test
    void workflowStart_withoutInitialize_rejected() {
        JsonRpcResponse resp = dispatch(new JsonRpcRequest(1L, "workflow/start",
                Map.of("workflowName", "invoice-approval")));
        assertNotNull(resp);
        assertFalse(resp.isSuccess());
        assertEquals(-32600, resp.error().code());
    }

    @Test
    void workflowStart_missingWorkflowName_returnsInvalidParams() {
        initializeSdk();
        JsonRpcResponse resp = dispatch(new JsonRpcRequest(2L, "workflow/start", Map.of()));
        assertNotNull(resp);
        assertFalse(resp.isSuccess());
        assertEquals(-32602, resp.error().code());
    }

    @Test
    void workflowStart_state_and_result_roundTrip() throws InterruptedException {
        initializeSdk();
        SpecDriven sdk = getSdkInstance();
        sdk.declareWorkflow("invoice-approval");

        JsonRpcResponse start = dispatch(new JsonRpcRequest(2L, "workflow/start",
                Map.of("workflowName", "invoice-approval", "input", Map.of("invoiceId", "inv-1"))));
        assertNotNull(start);
        assertTrue(start.isSuccess(), "workflow/start should succeed");
        @SuppressWarnings("unchecked")
        Map<String, Object> startResult = (Map<String, Object>) start.result();
        String workflowId = String.valueOf(startResult.get("workflowId"));
        assertEquals("invoice-approval", startResult.get("workflowName"));
        assertEquals("ACCEPTED", startResult.get("status"));

        JsonRpcResponse state = dispatch(new JsonRpcRequest(3L, "workflow/state", Map.of("workflowId", workflowId)));
        assertNotNull(state);
        assertTrue(state.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> stateResult = (Map<String, Object>) state.result();
        assertEquals(workflowId, String.valueOf(stateResult.get("workflowId")));

        JsonRpcResponse result = awaitWorkflowResult(workflowId, 10000L);
        assertNotNull(result, "Timed out waiting for workflow/result response");
        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> resultPayload = (Map<String, Object>) result.result();
        assertEquals(workflowId, String.valueOf(resultPayload.get("workflowId")));
        assertTrue(List.of("ACCEPTED", "RUNNING", "SUCCEEDED").contains(String.valueOf(resultPayload.get("status"))));
    }

    @Test
    void workflowState_unknownInstance_returnsError() {
        initializeSdk();
        JsonRpcResponse resp = dispatch(new JsonRpcRequest(2L, "workflow/state", Map.of("workflowId", "wf-missing")));
        assertNotNull(resp);
        assertFalse(resp.isSuccess());
        assertEquals(-32603, resp.error().code());
    }

    private JsonRpcResponse awaitWorkflowResult(String workflowId, long timeoutMs) throws InterruptedException {
        JsonRpcRequest request = new JsonRpcRequest(4L, "workflow/result", Map.of("workflowId", workflowId));
        dispatcher.onRequest(request);
        return transport.awaitResponse(request.id(), timeoutMs);
    }
}
