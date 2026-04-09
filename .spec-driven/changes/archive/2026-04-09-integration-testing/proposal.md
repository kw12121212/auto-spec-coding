# integration-testing

## What

End-to-end integration tests that verify behavioral consistency across the three interface layers (Native Java SDK, JSON-RPC over stdin, HTTP REST API). Each test runs the same logical scenario through all three layers and asserts identical observable outcomes — agent state transitions, tool invocation results, event emission, and error responses.

## Why

M16 is the project wrap-up milestone. All core milestones (M1–M10, M12–M15, M17–M19) are complete, but no test validates that the three interface layers agree on behavior for the same operation. The existing `JsonRpcEndToEndTest` and `HttpE2eTest` cover their respective stacks in isolation, but cross-layer consistency is unverified. Completing this change also unblocks M20 (Go SDK) and M21 (TypeScript SDK), which both list M16 as a dependency.

## Scope

- Parameterized integration test suite exercising the full demo flow through all three layers:
  create SDK → register tools → create agent → run with prompt → assert response and state
- Cross-layer consistency assertions: same prompt produces same agent state transitions, same tool call semantics, same error codes
- Error scenario consistency: missing auth, invalid tool, LLM failure — verified across HTTP and JSON-RPC
- Tool invocation round-trip through the orchestrator loop with a stub LLM provider
- Health / metadata endpoint parity (HTTP `/health`, JSON-RPC `initialize`, SDK `tools()`)

## Unchanged Behavior

- No changes to production code — this change only adds test code
- Existing `JsonRpcEndToEndTest` and `HttpE2eTest` remain untouched
- SDK, JSON-RPC, and HTTP layer implementations are not modified
