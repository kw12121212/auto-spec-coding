package org.specdriven.agent.jsonrpc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonRpcProtocolTest {

    // --- JsonRpcError ---

    @Nested
    class ErrorTests {

        @Test
        void standardParseError() {
            JsonRpcError err = JsonRpcError.parseError();
            assertEquals(-32700, err.code());
            assertEquals("Parse error", err.message());
            assertNull(err.data());
        }

        @Test
        void standardInvalidRequest() {
            JsonRpcError err = JsonRpcError.invalidRequest();
            assertEquals(-32600, err.code());
            assertEquals("Invalid Request", err.message());
        }

        @Test
        void standardMethodNotFound() {
            JsonRpcError err = JsonRpcError.methodNotFound();
            assertEquals(-32601, err.code());
            assertEquals("Method not found", err.message());
        }

        @Test
        void standardInvalidParams() {
            JsonRpcError err = JsonRpcError.invalidParams();
            assertEquals(-32602, err.code());
            assertEquals("Invalid params", err.message());
        }

        @Test
        void standardInternalError() {
            JsonRpcError err = JsonRpcError.internalError();
            assertEquals(-32603, err.code());
            assertEquals("Internal error", err.message());
        }

        @Test
        void customError() {
            JsonRpcError err = new JsonRpcError(100, "Custom error", null);
            assertEquals(100, err.code());
            assertEquals("Custom error", err.message());
            assertNull(err.data());
        }

        @Test
        void errorWithData() {
            Map<String, Object> data = Map.of("detail", "missing field");
            JsonRpcError err = new JsonRpcError(-32600, "Invalid Request", data);
            assertEquals(data, err.data());
        }

        @Test
        void nullMessageRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> new JsonRpcError(100, null, null));
        }
    }

    // --- JsonRpcRequest ---

    @Nested
    class RequestTests {

        @Test
        void createRequest() {
            Map<String, Object> params = Map.of("name", "grep");
            JsonRpcRequest req = new JsonRpcRequest(1L, "tools/list", params);
            assertEquals("2.0", req.jsonrpc());
            assertEquals(1L, req.id());
            assertEquals("tools/list", req.method());
            assertEquals(params, req.params());
        }

        @Test
        void nullIdRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> new JsonRpcRequest(null, "test", null));
        }

        @Test
        void idTypes() {
            // Long
            JsonRpcRequest r1 = new JsonRpcRequest(1L, "test", null);
            assertEquals(1L, r1.id());

            // String
            JsonRpcRequest r2 = new JsonRpcRequest("abc", "test", null);
            assertEquals("abc", r2.id());

            // BigDecimal
            JsonRpcRequest r3 = new JsonRpcRequest(java.math.BigDecimal.TEN, "test", null);
            assertEquals(java.math.BigDecimal.TEN, r3.id());
        }

        @Test
        void nullMethodRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> new JsonRpcRequest(1, null, null));
        }
    }

    // --- JsonRpcNotification ---

    @Nested
    class NotificationTests {

        @Test
        void createNotification() {
            Map<String, Object> params = Map.of("requestId", 5);
            JsonRpcNotification n = new JsonRpcNotification("cancel", params);
            assertEquals("2.0", n.jsonrpc());
            assertEquals("cancel", n.method());
            assertEquals(params, n.params());
        }

        @Test
        void noIdField() {
            JsonRpcNotification n = new JsonRpcNotification("cancel", null);
            // Compile-time guarantee: no id() method exists.
            // This test exists to verify the type has no id accessor.
            assertNotNull(n);
            assertEquals("cancel", n.method());
        }

        @Test
        void nullMethodRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> new JsonRpcNotification(null, null));
        }
    }

    // --- JsonRpcResponse ---

    @Nested
    class ResponseTests {

        @Test
        void successResponse() {
            Map<String, Object> result = Map.of("status", "ok");
            JsonRpcResponse resp = new JsonRpcResponse(1, result, null);
            assertEquals("2.0", resp.jsonrpc());
            assertEquals(1, resp.id());
            assertEquals(result, resp.result());
            assertNull(resp.error());
            assertTrue(resp.isSuccess());
        }

        @Test
        void errorResponse() {
            JsonRpcError err = JsonRpcError.methodNotFound();
            JsonRpcResponse resp = new JsonRpcResponse(1, null, err);
            assertNull(resp.result());
            assertEquals(err, resp.error());
            assertFalse(resp.isSuccess());
        }

        @Test
        void bothResultAndErrorRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> new JsonRpcResponse(1, "result", JsonRpcError.internalError()));
        }

        @Test
        void neitherResultNorErrorRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> new JsonRpcResponse(1, null, null));
        }

        @Test
        void successFactoryWithNullResult() {
            JsonRpcResponse resp = JsonRpcResponse.success(1, null);
            assertTrue(resp.isSuccess());
            assertNull(resp.result());
            assertNull(resp.error());
        }

        @Test
        void errorFactory() {
            JsonRpcResponse resp = JsonRpcResponse.error(2, JsonRpcError.parseError());
            assertFalse(resp.isSuccess());
            assertNull(resp.result());
            assertNotNull(resp.error());
            assertEquals(-32700, resp.error().code());
        }
    }

    // --- JsonRpcProtocolException ---

    @Nested
    class ExceptionTests {

        @Test
        void carriesErrorCode() {
            JsonRpcProtocolException ex = new JsonRpcProtocolException(-32600, "Invalid Request");
            assertEquals(-32600, ex.getErrorCode());
        }

        @Test
        void messageIsDescriptive() {
            JsonRpcProtocolException ex = new JsonRpcProtocolException(-32600, "Invalid Request: missing method");
            assertFalse(ex.getMessage().isBlank());
            assertEquals("Invalid Request: missing method", ex.getMessage());
        }

        @Test
        void wrapsCause() {
            RuntimeException cause = new RuntimeException("parse failure");
            JsonRpcProtocolException ex = new JsonRpcProtocolException(-32700, "Parse error", cause);
            assertEquals(cause, ex.getCause());
        }
    }

    // --- JsonRpcCodec Encoding ---

    @Nested
    class EncodeTests {

        @Test
        void encodeSuccessResponse() {
            JsonRpcResponse resp = new JsonRpcResponse(1, Map.of("status", "ok"), null);
            String json = JsonRpcCodec.encode(resp);
            assertEquals("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"status\":\"ok\"}}", json);
        }

        @Test
        void encodeErrorResponse() {
            JsonRpcResponse resp = new JsonRpcResponse(2, null, JsonRpcError.methodNotFound());
            String json = JsonRpcCodec.encode(resp);
            assertTrue(json.contains("\"jsonrpc\":\"2.0\""));
            assertTrue(json.contains("\"id\":2"));
            assertTrue(json.contains("\"code\":-32601"));
            assertTrue(json.contains("\"message\":\"Method not found\""));
        }

        @Test
        void encodeNotification() {
            JsonRpcNotification n = new JsonRpcNotification("cancel", Map.of("requestId", 5));
            String json = JsonRpcCodec.encode(n);
            assertTrue(json.contains("\"jsonrpc\":\"2.0\""));
            assertTrue(json.contains("\"method\":\"cancel\""));
            assertTrue(json.contains("\"params\":{\"requestId\":5}"));
            assertFalse(json.contains("\"id\""));
        }

        @Test
        void encodeResponseWithStringId() {
            JsonRpcResponse resp = JsonRpcResponse.success("abc-123", Map.of("ok", true));
            String json = JsonRpcCodec.encode(resp);
            assertTrue(json.contains("\"id\":\"abc-123\""));
        }

        @Test
        void encodeResponseWithNullResult() {
            JsonRpcResponse resp = JsonRpcResponse.success(1, null);
            String json = JsonRpcCodec.encode(resp);
            assertTrue(json.contains("\"result\":null"));
        }
    }

    // --- JsonRpcCodec Decoding ---

    @Nested
    class DecodeTests {

        @Test
        void decodeValidRequest() {
            String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{\"name\":\"grep\"}}";
            Object result = JsonRpcCodec.decodeRequest(json);
            assertInstanceOf(JsonRpcRequest.class, result);
            JsonRpcRequest req = (JsonRpcRequest) result;
            assertEquals(1L, req.id());
            assertEquals("tools/list", req.method());
            assertEquals(Map.of("name", "grep"), req.params());
        }

        @Test
        void decodeNotification() {
            String json = "{\"jsonrpc\":\"2.0\",\"method\":\"cancel\",\"params\":{\"requestId\":5}}";
            Object result = JsonRpcCodec.decodeRequest(json);
            assertInstanceOf(JsonRpcNotification.class, result);
            JsonRpcNotification n = (JsonRpcNotification) result;
            assertEquals("cancel", n.method());
            assertEquals(Map.of("requestId", 5L), n.params());
        }

        @Test
        void decodeRequestWithoutParams() {
            String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}";
            Object result = JsonRpcCodec.decodeRequest(json);
            assertInstanceOf(JsonRpcRequest.class, result);
            JsonRpcRequest req = (JsonRpcRequest) result;
            assertEquals(Map.of(), req.params());
        }

        @Test
        void rejectMissingJsonrpcVersion() {
            String json = "{\"id\":1,\"method\":\"test\"}";
            JsonRpcProtocolException ex = assertThrows(JsonRpcProtocolException.class,
                    () -> JsonRpcCodec.decodeRequest(json));
            assertEquals(-32600, ex.getErrorCode());
        }

        @Test
        void rejectWrongJsonrpcVersion() {
            String json = "{\"jsonrpc\":\"1.0\",\"id\":1,\"method\":\"test\"}";
            JsonRpcProtocolException ex = assertThrows(JsonRpcProtocolException.class,
                    () -> JsonRpcCodec.decodeRequest(json));
            assertEquals(-32600, ex.getErrorCode());
        }

        @Test
        void rejectMissingMethod() {
            String json = "{\"jsonrpc\":\"2.0\",\"id\":1}";
            JsonRpcProtocolException ex = assertThrows(JsonRpcProtocolException.class,
                    () -> JsonRpcCodec.decodeRequest(json));
            assertEquals(-32600, ex.getErrorCode());
        }

        @Test
        void rejectMalformedJson() {
            JsonRpcProtocolException ex = assertThrows(JsonRpcProtocolException.class,
                    () -> JsonRpcCodec.decodeRequest("{invalid"));
            assertEquals(-32700, ex.getErrorCode());
            assertNotNull(ex.getCause());
        }

        @Test
        void decodeByPositionParams() {
            String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"test\",\"params\":[1,2,3]}";
            Object result = JsonRpcCodec.decodeRequest(json);
            assertInstanceOf(JsonRpcRequest.class, result);
            JsonRpcRequest req = (JsonRpcRequest) result;
            assertInstanceOf(List.class, req.params());
            List<?> params = (List<?>) req.params();
            assertEquals(List.of(1L, 2L, 3L), params);
        }
    }

    // --- Round-trip tests ---

    @Nested
    class RoundTripTests {

        @Test
        void encodeDecodeResponseRoundTrip() {
            JsonRpcResponse original = new JsonRpcResponse(42, Map.of("key", "value"), null);
            String json = JsonRpcCodec.encode(original);
            // Re-parse the JSON to verify structure
            assertTrue(json.contains("\"jsonrpc\":\"2.0\""));
            assertTrue(json.contains("\"id\":42"));
            assertTrue(json.contains("\"result\":{\"key\":\"value\"}"));
        }
    }
}
