---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/json/JsonReader.java
    - src/main/java/org/specdriven/agent/json/JsonWriter.java
    - src/main/java/org/specdriven/agent/jsonrpc/JsonRpcCodec.java
    - src/main/java/org/specdriven/agent/jsonrpc/JsonRpcError.java
    - src/main/java/org/specdriven/agent/jsonrpc/JsonRpcNotification.java
    - src/main/java/org/specdriven/agent/jsonrpc/JsonRpcProtocolException.java
    - src/main/java/org/specdriven/agent/jsonrpc/JsonRpcRequest.java
    - src/main/java/org/specdriven/agent/jsonrpc/JsonRpcResponse.java
  tests:
    - src/test/java/org/specdriven/agent/json/JsonReaderTest.java
    - src/test/java/org/specdriven/agent/json/JsonWriterTest.java
    - src/test/java/org/specdriven/agent/jsonrpc/JsonRpcProtocolTest.java
---

# jsonrpc-protocol.md

## ADDED Requirements

### Requirement: JsonRpcRequest type

The system MUST provide an immutable `JsonRpcRequest` type in `org.specdriven.agent.jsonrpc` representing a JSON-RPC 2.0 request.

#### Scenario: Create a request
- GIVEN a method name `"tools/list"` and params `{"name": "grep"}`
- WHEN a `JsonRpcRequest` is created with `id=1`, `method="tools/list"`, and `params=Map.of("name", "grep")`
- THEN `jsonrpc()` MUST return `"2.0"`
- AND `id()` MUST return `1`
- AND `method()` MUST return `"tools/list"`
- AND `params()` MUST return the provided map

#### Scenario: Request with null id is invalid for a request
- GIVEN a `JsonRpcRequest` construction
- WHEN `id` is null
- THEN the constructor MUST throw `IllegalArgumentException`

#### Scenario: Request id types
- GIVEN valid construction arguments
- WHEN `id` is a `Long`, `String`, or `BigDecimal`
- THEN the request MUST accept it without error

### Requirement: JsonRpcNotification type

The system MUST provide an immutable `JsonRpcNotification` type in `org.specdriven.agent.jsonrpc` representing a JSON-RPC 2.0 notification (request without an id).

#### Scenario: Create a notification
- GIVEN a method name `"cancel"` and params `{"requestId": 5}`
- WHEN a `JsonRpcNotification` is created with `method="cancel"` and `params=Map.of("requestId", 5)`
- THEN `jsonrpc()` MUST return `"2.0"`
- AND `method()` MUST return `"cancel"`
- AND `params()` MUST return the provided map

#### Scenario: Notification has no id accessor
- GIVEN a `JsonRpcNotification` instance
- THEN it MUST NOT expose an `id` field or accessor

### Requirement: JsonRpcResponse type

The system MUST provide an immutable `JsonRpcResponse` type in `org.specdriven.agent.jsonrpc` representing a JSON-RPC 2.0 response.

#### Scenario: Success response
- GIVEN a result value `Map.of("status", "ok")`
- WHEN a `JsonRpcResponse` is created with `id=1` and `result=Map.of("status", "ok")`
- THEN `jsonrpc()` MUST return `"2.0"`
- AND `id()` MUST return `1`
- AND `result()` MUST return the provided result
- AND `error()` MUST return null

#### Scenario: Error response
- GIVEN a `JsonRpcError`
- WHEN a `JsonRpcResponse` is created with `id=1` and the error
- THEN `result()` MUST return null
- AND `error()` MUST return the provided error

#### Scenario: Both result and error is rejected
- GIVEN a result and an error
- WHEN a `JsonRpcResponse` is constructed with both non-null `result` and non-null `error`
- THEN the constructor MUST throw `IllegalArgumentException`

#### Scenario: Neither result nor error is rejected
- GIVEN null result and null error
- WHEN a `JsonRpcResponse` is constructed
- THEN the constructor MUST throw `IllegalArgumentException`

### Requirement: JsonRpcError type

The system MUST provide an immutable `JsonRpcError` type in `org.specdriven.agent.jsonrpc` representing a JSON-RPC 2.0 error object.

#### Scenario: Standard error
- GIVEN standard error code `-32600`
- WHEN `JsonRpcError.invalidRequest()` is called
- THEN `code()` MUST return `-32600`
- AND `message()` MUST return `"Invalid Request"`
- AND `data()` MUST return null

#### Scenario: Custom error
- GIVEN custom code `100` and message `"Custom error"`
- WHEN a `JsonRpcError` is created with these values
- THEN `code()` MUST return `100`
- AND `message()` MUST return `"Custom error"`

#### Scenario: Error with data
- GIVEN a `JsonRpcError` with `data=Map.of("detail", "missing field")`
- THEN `data()` MUST return the provided data map

#### Scenario: Predefined error constants
- THEN the system MUST provide static factory methods for all standard errors:
  - `parseError()` → code `-32700`
  - `invalidRequest()` → code `-32600`
  - `methodNotFound()` → code `-32601`
  - `invalidParams()` → code `-32602`
  - `internalError()` → code `-32603`

### Requirement: JsonRpcCodec encoding

The system MUST provide a `JsonRpcCodec` class in `org.specdriven.agent.jsonrpc` with static methods for encoding protocol types to JSON strings.

#### Scenario: Encode success response
- GIVEN a `JsonRpcResponse` with `id=1` and `result=Map.of("status", "ok")`
- WHEN `JsonRpcCodec.encode(response)` is called
- THEN the result MUST be a valid JSON string `{"jsonrpc":"2.0","id":1,"result":{"status":"ok"}}`
- AND field order MUST be `jsonrpc`, `id`, `result` (or `error`)

#### Scenario: Encode error response
- GIVEN a `JsonRpcResponse` with `id=2` and `error=JsonRpcError.methodNotFound()`
- WHEN `JsonRpcCodec.encode(response)` is called
- THEN the result MUST contain `"jsonrpc":"2.0"`, `"id":2`, and the error object with `"code":-32601` and `"message":"Method not found"`

#### Scenario: Encode notification
- GIVEN a `JsonRpcNotification` with `method="cancel"` and `params=Map.of("requestId", 5)`
- WHEN `JsonRpcCodec.encode(notification)` is called
- THEN the result MUST contain `"jsonrpc":"2.0"`, `"method":"cancel"`, and `"params":{"requestId":5}`
- AND MUST NOT contain an `"id"` field

#### Scenario: Encode response with string id
- GIVEN a `JsonRpcResponse` with `id="abc-123"`
- WHEN `JsonRpcCodec.encode(response)` is called
- THEN the result MUST contain `"id":"abc-123"`

#### Scenario: Encode response with null result
- GIVEN a `JsonRpcResponse` that is a success response with `result=null`
- WHEN `JsonRpcCodec.encode(response)` is called
- THEN the result MUST contain `"result":null`

### Requirement: JsonRpcCodec decoding

The system MUST provide static methods on `JsonRpcCodec` for decoding JSON strings into protocol types.

#### Scenario: Decode valid request
- GIVEN a JSON string `{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{"name":"grep"}}`
- WHEN `JsonRpcCodec.decodeRequest(json)` is called
- THEN it MUST return a `JsonRpcRequest` with `id=1L`, `method="tools/list"`, `params=Map.of("name", "grep")`

#### Scenario: Decode notification
- GIVEN a JSON string `{"jsonrpc":"2.0","method":"cancel","params":{"requestId":5}}`
- WHEN `JsonRpcCodec.decodeRequest(json)` is called
- THEN it MUST return a `JsonRpcNotification` (not a `JsonRpcRequest`)

#### Scenario: Decode request without params
- GIVEN a JSON string `{"jsonrpc":"2.0","id":1,"method":"initialize"}`
- WHEN `JsonRpcCodec.decodeRequest(json)` is called
- THEN it MUST return a `JsonRpcRequest` with `params=Map.of()`

#### Scenario: Reject missing jsonrpc version
- GIVEN a JSON string `{"id":1,"method":"test"}`
- WHEN `JsonRpcCodec.decodeRequest(json)` is called
- THEN it MUST throw `JsonRpcProtocolException` indicating invalid request

#### Scenario: Reject wrong jsonrpc version
- GIVEN a JSON string `{"jsonrpc":"1.0","id":1,"method":"test"}`
- WHEN `JsonRpcCodec.decodeRequest(json)` is called
- THEN it MUST throw `JsonRpcProtocolException`

#### Scenario: Reject missing method
- GIVEN a JSON string `{"jsonrpc":"2.0","id":1}`
- WHEN `JsonRpcCodec.decodeRequest(json)` is called
- THEN it MUST throw `JsonRpcProtocolException`

#### Scenario: Reject malformed JSON
- GIVEN a non-JSON string `{invalid`
- WHEN `JsonRpcCodec.decodeRequest(json)` is called
- THEN it MUST throw `JsonRpcProtocolException` wrapping the parse error

#### Scenario: Decode by-position params
- GIVEN a JSON string `{"jsonrpc":"2.0","id":1,"method":"test","params":[1,2,3]}`
- WHEN `JsonRpcCodec.decodeRequest(json)` is called
- THEN it MUST return a `JsonRpcRequest` with `params` as a `List<Object>` containing `[1L, 2L, 3L]`

### Requirement: JsonRpcProtocolException

The system MUST provide a `JsonRpcProtocolException` in `org.specdriven.agent.jsonrpc` for protocol-level decode errors.

#### Scenario: Exception carries error code
- GIVEN a decode failure due to invalid request
- WHEN `JsonRpcProtocolException` is thrown
- THEN `getErrorCode()` MUST return the corresponding JSON-RPC error code (e.g. `-32600`)

#### Scenario: Exception message is descriptive
- GIVEN any `JsonRpcProtocolException`
- THEN `getMessage()` MUST return a non-empty string describing the protocol violation
