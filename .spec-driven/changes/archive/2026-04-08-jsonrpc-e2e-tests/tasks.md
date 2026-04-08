# Tasks: jsonrpc-e2e-tests

## Implementation

- [x] Create `JsonRpcEndToEndTest.java` in `src/test/java/org/specdriven/agent/jsonrpc/` with helper methods `frame()` and `parseResponses()`
- [x] E2E test: full lifecycle — send framed `initialize` → `agent/state` → `tools/list` → `shutdown` requests, verify all four framed responses are correct
- [x] E2E test: error scenarios — `methodNotFound` (unknown method), `invalidParams` (agent/run without prompt), uninitialized (agent/run before initialize), post-shutdown rejected
- [x] E2E test: notification handling — send framed `$/cancel` notification, verify no response is produced and transport continues reading
- [x] E2E test: multi-frame input — send two consecutive framed requests in one input stream, verify both responses appear in output in order
- [x] E2E test: event forwarding — trigger an SDK operation that emits events, verify JSON-RPC notification with `method="event"` appears on output
- [x] E2E test: `agent/run` integration — send framed `initialize` + `agent/run` with prompt, verify response contains `output` field

## Testing

- [x] Validate: run `mvn compile` — project compiles without errors
- [x] Unit test: run `mvn test` — all E2E tests and existing tests pass

## Verification

- [x] Verify test coverage includes all methods: initialize, shutdown, agent/run, agent/stop, agent/state, tools/list
- [x] Verify error code responses match JSON-RPC 2.0 spec (-32600, -32601, -32602, -32603)
- [x] Verify Content-Length framing is correct on both request and response sides
