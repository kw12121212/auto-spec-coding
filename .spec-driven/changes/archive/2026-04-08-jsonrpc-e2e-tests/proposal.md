# jsonrpc-e2e-tests

## What

End-to-end integration tests that exercise the full JSON-RPC stack from transport framing through protocol codec, dispatcher routing, SDK invocation, and response output — validating the complete request-response lifecycle over stdin/stdout.

## Why

M13 has three completed changes (jsonrpc-protocol, jsonrpc-transport, jsonrpc-handlers), each with their own unit tests. However, no test currently validates the full pipeline: a framed message arriving on stdin → codec decode → dispatcher route → SDK operation → codec encode → framed response on stdout. E2E tests are the final item needed to close M13 and are explicitly listed in the milestone Done Criteria: "有端到端测试验证完整请求-响应流程".

## Scope

In scope:
- E2E test class `JsonRpcEndToEndTest` wiring `StdioTransport` + `JsonRpcDispatcher` over real `ByteArrayInputStream`/`ByteArrayOutputStream`
- Full lifecycle scenario: `initialize` → `agent/state` → `tools/list` → `shutdown`
- Error scenario: method not found, missing params, uninitialized state, shutdown-then-call
- Event forwarding scenario: SDK event emitted → JSON-RPC notification sent on transport
- Notification scenario: `$/cancel` received during an in-flight request
- Multi-frame scenario: two consecutive requests in a single input stream, both answered correctly

Out of scope:
- Batch request support (explicitly out of scope per M13 milestone)
- Performance / concurrency stress tests (unit tests cover thread safety)
- HTTP transport (M14)

## Unchanged Behavior

No production code changes. All existing unit tests must continue to pass.
