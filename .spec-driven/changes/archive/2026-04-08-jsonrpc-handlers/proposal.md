# jsonrpc-handlers

## What

Implement request routing and handler dispatch that maps JSON-RPC 2.0 method calls to SDK operations over the existing stdin/stdout transport layer.

## Why

The protocol types (M13 `jsonrpc-protocol`) and transport framing (M13 `jsonrpc-transport`) are complete, but there is no logic to route inbound JSON-RPC requests to actual SDK operations. Without handlers, the JSON-RPC layer is a pipe with no processing. This change closes the gap between raw message transport and usable agent functionality.

## Scope

- Define a `JsonRpcDispatcher` that implements `JsonRpcMessageHandler` and routes requests by method name to typed handler methods
- Implement handlers for core methods: `initialize`, `shutdown`, `agent/run`, `agent/stop`, `agent/state`, `tools/list`
- Forward agent events as JSON-RPC notifications to the connected client
- Map SDK exceptions to appropriate JSON-RPC error responses
- Support the `$/cancel` notification for cancelling in-flight requests

## Unchanged Behavior

- Protocol types and codec behavior unchanged
- Transport framing and lifecycle unchanged
- SDK public API surface unchanged — handlers are a thin routing layer on top
