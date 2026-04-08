# Tasks: jsonrpc-handlers

## Implementation

- [x] Create `JsonRpcDispatcher` class in `org.specdriven.agent.jsonrpc` implementing `JsonRpcMessageHandler`
- [x] Implement method dispatch map: route `onRequest` by method name to typed handler methods
- [x] Implement `initialize` handler: bootstrap `SpecDriven` SDK from optional configPath/systemPrompt params, return version and capabilities
- [x] Implement `shutdown` handler: close the `SpecDriven` instance and release resources
- [x] Implement `agent/run` handler: create `SdkAgent`, run prompt, return output string; submit run asynchronously
- [x] Implement `agent/stop` handler: stop the currently running agent
- [x] Implement `agent/state` handler: return current agent state as string
- [x] Implement `tools/list` handler: enumerate registered tools with name, description, and parameter schemas
- [x] Implement `$/cancel` notification handler in `onNotification`: cancel the in-flight request matching the given ID
- [x] Implement event forwarding: register `SdkEventListener` that converts SDK events to JSON-RPC notifications and sends them via transport
- [x] Implement SDK exception → JSON-RPC error mapping for all `SdkException` subtypes
- [x] Return `methodNotFound` (-32601) for unrecognized methods, `invalidParams` (-32602) for missing/invalid params

## Testing

- [x] Validate with `mvn compile` — lint and build check
- [x] Run `mvn test` — unit tests for all dispatcher behavior
- [x] Unit test: dispatcher routes `initialize` to correct handler and returns version/capabilities
- [x] Unit test: dispatcher routes `agent/run` and returns agent output
- [x] Unit test: dispatcher routes `agent/stop` and returns null result
- [x] Unit test: dispatcher routes `tools/list` and returns tool schemas
- [x] Unit test: dispatcher returns `-32601` for unknown method
- [x] Unit test: dispatcher returns `-32602` for missing required params on `agent/run`
- [x] Unit test: SDK exceptions are mapped to correct JSON-RPC error codes
- [x] Unit test: `$/cancel` notification cancels in-flight request
- [x] Unit test: events from agent are forwarded as JSON-RPC notifications

## Verification

- [x] Verify implementation matches proposal scope (no extra methods beyond the 6 defined)
- [x] Verify error mapping covers all `SdkException` subtypes
- [x] Verify dispatcher integrates with existing `JsonRpcTransport` and `JsonRpcCodec` without modification
