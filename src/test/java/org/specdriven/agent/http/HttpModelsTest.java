package org.specdriven.agent.http;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpModelsTest {

    // --- Record construction tests ---

    @Nested
    class RunAgentRequestTests {

        @Test
        void constructWithRequiredFieldsOnly() {
            RunAgentRequest req = new RunAgentRequest("explain this code", null, null, null);
            assertEquals("explain this code", req.prompt());
            assertNull(req.systemPrompt());
            assertNull(req.maxTurns());
            assertNull(req.toolTimeoutSeconds());
        }

        @Test
        void constructWithAllFields() {
            RunAgentRequest req = new RunAgentRequest("review", "You are a reviewer", 10, 30);
            assertEquals("review", req.prompt());
            assertEquals("You are a reviewer", req.systemPrompt());
            assertEquals(10, req.maxTurns());
            assertEquals(30, req.toolTimeoutSeconds());
        }

        @Test
        void nullPromptRejected() {
            assertThrows(NullPointerException.class, () -> new RunAgentRequest(null, null, null, null));
        }
    }

    @Nested
    class RunAgentResponseTests {

        @Test
        void successResponse() {
            RunAgentResponse resp = new RunAgentResponse("abc-123", "explanation text", "STOPPED");
            assertEquals("abc-123", resp.agentId());
            assertEquals("explanation text", resp.output());
            assertEquals("STOPPED", resp.state());
        }

        @Test
        void runningResponseWithNoOutput() {
            RunAgentResponse resp = new RunAgentResponse("abc-123", null, "RUNNING");
            assertNull(resp.output());
        }
    }

    @Nested
    class AgentStateResponseTests {

        @Test
        void stateResponse() {
            AgentStateResponse resp = new AgentStateResponse("abc-123", "RUNNING", 1000L, 2000L);
            assertEquals("abc-123", resp.agentId());
            assertEquals("RUNNING", resp.state());
            assertEquals(1000L, resp.createdAt());
            assertEquals(2000L, resp.updatedAt());
        }
    }

    @Nested
    class ToolInfoTests {

        @Test
        void toolInfoWithParameters() {
            ToolInfo info = new ToolInfo("bash", "Execute shell commands",
                    List.of(Map.of("name", "command", "type", "string")));
            assertEquals("bash", info.name());
            assertEquals("Execute shell commands", info.description());
            assertEquals(1, info.parameters().size());
        }

        @Test
        void nullParametersBecomesEmptyList() {
            ToolInfo info = new ToolInfo("health", "Health check", null);
            assertNotNull(info.parameters());
            assertTrue(info.parameters().isEmpty());
        }
    }

    @Nested
    class ToolsListResponseTests {

        @Test
        void listWithTools() {
            ToolsListResponse resp = new ToolsListResponse(
                    List.of(new ToolInfo("bash", "run", null)));
            assertEquals(1, resp.tools().size());
        }

        @Test
        void nullToolsBecomesEmptyList() {
            ToolsListResponse resp = new ToolsListResponse(null);
            assertNotNull(resp.tools());
            assertTrue(resp.tools().isEmpty());
        }
    }

    @Nested
    class HealthResponseTests {

        @Test
        void healthyResponse() {
            HealthResponse resp = new HealthResponse("ok", "0.1.0");
            assertEquals("ok", resp.status());
            assertEquals("0.1.0", resp.version());
        }
    }

    @Nested
    class ErrorResponseTests {

        @Test
        void errorWithDetails() {
            ErrorResponse err = new ErrorResponse(400, "invalid_params", "Missing prompt",
                    Map.of("field", "prompt"));
            assertEquals(400, err.status());
            assertEquals("invalid_params", err.error());
            assertEquals("Missing prompt", err.message());
            assertNotNull(err.details());
        }

        @Test
        void errorWithoutDetails() {
            ErrorResponse err = new ErrorResponse(500, "internal", "Unexpected error", null);
            assertNull(err.details());
        }
    }

    @Nested
    class HttpApiExceptionTests {

        @Test
        void carriesHttpStatus() {
            HttpApiException ex = new HttpApiException(400, "invalid_params", "Missing prompt");
            assertEquals(400, ex.httpStatus());
            assertEquals("invalid_params", ex.errorCode());
            assertEquals("Missing prompt", ex.getMessage());
        }

        @Test
        void toErrorResponse() {
            HttpApiException ex = new HttpApiException(400, "invalid_params", "Missing prompt");
            ErrorResponse err = ex.toErrorResponse();
            assertEquals(400, err.status());
            assertEquals("invalid_params", err.error());
            assertEquals("Missing prompt", err.message());
            assertNull(err.details());
        }
    }

    // --- Codec encode tests ---

    @Nested
    class EncodeTests {

        @Test
        void encodeRunAgentResponse() {
            RunAgentResponse resp = new RunAgentResponse("abc", "hi", "STOPPED");
            String json = HttpJsonCodec.encode(resp);
            assertTrue(json.contains("\"agentId\":\"abc\""));
            assertTrue(json.contains("\"output\":\"hi\""));
            assertTrue(json.contains("\"state\":\"STOPPED\""));
        }

        @Test
        void encodeAgentStateResponse() {
            AgentStateResponse resp = new AgentStateResponse("id1", "RUNNING", 100L, 200L);
            String json = HttpJsonCodec.encode(resp);
            assertTrue(json.contains("\"agentId\":\"id1\""));
            assertTrue(json.contains("\"state\":\"RUNNING\""));
            assertTrue(json.contains("\"createdAt\":100"));
            assertTrue(json.contains("\"updatedAt\":200"));
        }

        @Test
        void encodeHealthResponse() {
            HealthResponse resp = new HealthResponse("ok", "0.1.0");
            String json = HttpJsonCodec.encode(resp);
            assertTrue(json.contains("\"status\":\"ok\""));
            assertTrue(json.contains("\"version\":\"0.1.0\""));
        }

        @Test
        void encodeErrorResponse() {
            ErrorResponse err = new ErrorResponse(400, "bad_request", "invalid", null);
            String json = HttpJsonCodec.encode(err);
            assertTrue(json.contains("\"status\":400"));
            assertTrue(json.contains("\"error\":\"bad_request\""));
            assertTrue(json.contains("\"message\":\"invalid\""));
        }

        @Test
        void encodeErrorResponseWithDetails() {
            ErrorResponse err = new ErrorResponse(400, "bad_request", "invalid",
                    Map.of("field", "prompt"));
            String json = HttpJsonCodec.encode(err);
            assertTrue(json.contains("\"details\":"));
            assertTrue(json.contains("\"field\":\"prompt\""));
        }

        @Test
        void encodeToolsListResponse() {
            ToolsListResponse resp = new ToolsListResponse(List.of(
                    new ToolInfo("bash", "run commands", List.of(Map.of("name", "cmd"))),
                    new ToolInfo("grep", "search content", null)));
            String json = HttpJsonCodec.encode(resp);
            assertTrue(json.contains("\"tools\":["));
            assertTrue(json.contains("\"name\":\"bash\""));
            assertTrue(json.contains("\"name\":\"grep\""));
        }
    }

    // --- Codec decode tests ---

    @Nested
    class DecodeTests {

        @Test
        void decodeRunAgentRequest() {
            String json = "{\"prompt\":\"explain this code\"}";
            RunAgentRequest req = HttpJsonCodec.decodeRequest(json);
            assertEquals("explain this code", req.prompt());
            assertNull(req.systemPrompt());
            assertNull(req.maxTurns());
            assertNull(req.toolTimeoutSeconds());
        }

        @Test
        void decodeRunAgentRequestWithAllFields() {
            String json = "{\"prompt\":\"review\",\"systemPrompt\":\"You are a reviewer\",\"maxTurns\":10,\"toolTimeoutSeconds\":30}";
            RunAgentRequest req = HttpJsonCodec.decodeRequest(json);
            assertEquals("review", req.prompt());
            assertEquals("You are a reviewer", req.systemPrompt());
            assertEquals(10, req.maxTurns());
            assertEquals(30, req.toolTimeoutSeconds());
        }

        @Test
        void rejectMissingRequiredField() {
            String json = "{\"systemPrompt\":\"You are a reviewer\"}";
            HttpApiException ex = assertThrows(HttpApiException.class,
                    () -> HttpJsonCodec.decodeRequest(json));
            assertEquals(400, ex.httpStatus());
            assertEquals("invalid_params", ex.errorCode());
        }
    }
}
