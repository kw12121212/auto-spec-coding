# jsonrpc-protocol

## What

Define JSON-RPC 2.0 protocol types (request, response, notification, error) and their encode/decode codec in the `org.specdriven.agent.jsonrpc` package, using the project's existing `JsonReader`/`JsonWriter` utilities with no external dependencies.

## Why

This is the foundation for M13 (JSON-RPC Interface). JSON-RPC is the first external interface layer that unblocks M20/M21 client SDKs and M16 integration. The protocol types must exist before the transport (`jsonrpc-transport`) and handler mapping (`jsonrpc-handlers`) changes can be built.

## Scope

- Immutable value types: `JsonRpcRequest`, `JsonRpcResponse`, `JsonRpcNotification`, `JsonRpcError`
- `JsonRpcError` with standard JSON-RPC 2.0 error codes (`-32700` Parse error, `-32600` Invalid Request, `-32601` Method not found, `-32602` Invalid params, `-32603` Internal error) plus support for custom error codes
- `JsonRpcCodec` for encoding responses/notifications to JSON and decoding requests/notifications from JSON
- Unit tests for all types and codec round-trips

## Unchanged Behavior

- Existing `JsonReader`/`JsonWriter` APIs remain unchanged
- SDK public API (`SpecDriven`, `SdkAgent`, `SdkBuilder`) is not affected
- Agent lifecycle, tool surface, and LLM provider layer are not affected
