# Tasks: integration-testing

## Implementation

- [x] Create `CrossLayerConsistencyTest.java` under `src/test/java/org/specdriven/agent/integration/`
- [x] Implement `StubLlmProvider` fixture that returns deterministic responses in sequence (text → tool-call → text)
- [x] Implement `StubTool` fixture with predictable input/output for tool call verification
- [x] Add shared `@BeforeAll` setup: build `SpecDriven` SDK with stub provider and stub tool
- [x] Add HTTP harness setup: embedded Tomcat with `HttpApiServlet` + `AuthFilter` on random port
- [x] Add JSON-RPC harness setup: `ByteArrayInputStream`/`ByteArrayOutputStream` with `StdioTransport` + `JsonRpcDispatcher`
- [x] Test: happy path agent run through all three layers — assert STOPPED state and response content
- [x] Test: tool call round-trip through orchestrator loop — assert tool invoked and result flows back through all layers
- [x] Test: agent state query parity across SDK, JSON-RPC `agent/state`, HTTP `GET /api/v1/agent/state`
- [x] Test: tools list parity across SDK `tools()`, JSON-RPC `tools/list`, HTTP `GET /api/v1/tools`
- [x] Test: error consistency — invalid/missing prompt returns structured error through all layers
- [x] Test: HTTP 401 unauthorized — verify auth filter rejects unauthenticated requests

## Testing

- [x] Validation: run `mvn compile -q` to verify no production code was changed and project compiles
- [x] Unit test: run `mvn test -pl . -Dtest="CrossLayerConsistencyTest" -Dsurefire.useFile=false` to execute the new cross-layer consistency tests
- [x] Regression: run `mvn test -Dsurefire.useFile=false` to confirm full suite passes with no regressions

## Verification

- [x] All cross-layer consistency tests pass
- [x] No production code changes introduced
- [x] Existing `JsonRpcEndToEndTest` and `HttpE2eTest` still pass
