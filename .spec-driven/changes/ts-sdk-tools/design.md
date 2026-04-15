# Design: ts-sdk-tools

## Approach

Mirror the Go SDK's tool implementation (`go-sdk/specdriven/tools.go`) adapted for TypeScript/Node.js idioms:

1. **`tools.ts`** — new file containing `ToolHandler` type, `ToolCallbackHandler` class, and tool-related models (`RemoteToolRegistrationRequest`, `RemoteToolInvocationRequest`, `RemoteToolInvocationResponse`, `ToolRegistrationResult`).

2. **`ToolCallbackHandler`** — a handler class that the caller mounts on a Node.js HTTP server. It holds a `Map<string, ToolHandler>` of registered local handlers. On each POST, it decodes `RemoteToolInvocationRequest`, looks up the handler, calls it, and writes `RemoteToolInvocationResponse` JSON. Non-POST requests and invalid JSON receive structured error responses.

3. **`SpecDrivenClient.registerRemoteTool()`** — sends `POST /api/v1/tools/register` with `RemoteToolRegistrationRequest` JSON. Returns the decoded `ToolRegistrationResult` (mirrors `ToolInfo`). Uses existing `request<T>()` infrastructure with authentication and retry.

4. **`index.ts`** — re-exports new public surface: `ToolCallbackHandler`, `ToolHandler`, and the new model types.

The callback server itself (binding a port, calling `http.createServer`) is left to the caller. `ToolCallbackHandler` only provides the dispatch logic as a standalone class, matching the Go SDK's `ServeHTTP` handler pattern.

## Key Decisions

- **Callback/push model, not pull** — the Java `RemoteHttpTool` requires a `callbackUrl` and POSTs to it during agent execution. There is no backend polling endpoint for pending tool invocations. The TS SDK must expose an HTTP endpoint reachable by the Java backend.
- **Caller owns the server lifecycle** — `ToolCallbackHandler` does not create or bind an HTTP server. The caller creates the server (`http.createServer(handler.handleRequest.bind(handler))`), binds it to a port, and provides that URL when registering tools. This keeps the SDK free of lifecycle assumptions.
- **`ToolHandler` signature `(parameters) => Promise<string>`** — matches the Go SDK's `func(ctx, map) (string, error)` semantics, simplified without context threading since TypeScript async/await handles cancellation through other means.
- **Models co-located in `tools.ts`** — registration and invocation models are specific to tool callbacks and live in the same file rather than `models.ts`, which only holds HTTP API response shapes used by the client.

## Alternatives Considered

- **Pull/polling model** — proposed during planning but ruled out because the Java backend has no endpoint for pending tool invocations. All tool calls are push-only via `callbackUrl`.
- **Bundling tool models into `models.ts`** — rejected to keep `models.ts` focused on HTTP API request/response shapes and avoid mixing callback-server concerns with client models.
- **`ToolCallbackHandler` as a full HTTP server** — rejected to avoid lifecycle coupling. Callers may already have an HTTP server (e.g., Express app), and imposing a built-in server would prevent reuse.
