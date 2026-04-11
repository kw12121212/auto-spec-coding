---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/jsonrpc/JsonRpcCodec.java
    - src/main/java/org/specdriven/agent/jsonrpc/JsonRpcDispatcher.java
    - src/main/java/org/specdriven/agent/jsonrpc/StdioTransport.java
  tests:
    - src/test/java/org/specdriven/agent/jsonrpc/JsonRpcEndToEndTest.java
---

# jsonrpc-e2e-tests.md

## ADDED Requirements

### Requirement: JSON-RPC end-to-end integration tests

The system MUST provide end-to-end tests that validate the complete JSON-RPC pipeline from transport framing through SDK invocation and response output.

#### Scenario: Full lifecycle
- GIVEN a `StdioTransport` connected to a `JsonRpcDispatcher` over byte array streams
- WHEN framed requests for `initialize`, `agent/state`, `tools/list`, `shutdown` are sent in sequence
- THEN each request MUST produce a correctly framed JSON-RPC response on the output stream
- AND the `initialize` response MUST contain `version` and `capabilities`
- AND the `tools/list` response MUST contain a `tools` array
- AND the `shutdown` response MUST have `result=null`

#### Scenario: Error scenarios end-to-end
- GIVEN the full stack wired over byte array streams
- WHEN an unknown method is sent, the response MUST have error code `-32601`
- WHEN `agent/run` is sent before `initialize`, the response MUST have error code `-32600`
- WHEN `agent/run` is sent without `prompt`, the response MUST have error code `-32602`
- WHEN an operation is sent after `shutdown`, the response MUST have error code `-32600`

#### Scenario: Multi-frame input
- GIVEN two consecutive framed requests in a single input stream
- WHEN the transport reads them
- THEN both responses MUST appear in the output stream in order
- AND each response MUST be correctly framed with Content-Length header

#### Scenario: Event forwarding end-to-end
- GIVEN an initialized dispatcher
- WHEN the SDK emits an event during agent execution
- THEN a framed JSON-RPC notification with `method="event"` MUST appear on the output stream

#### Scenario: Notification handling end-to-end
- GIVEN an initialized dispatcher
- WHEN a framed `$/cancel` notification is received
- THEN no response MUST be sent
- AND the transport MUST continue reading subsequent messages
