# ts-sdk-tools

## What

Add tool registration and callback-backed invocation support to the TypeScript SDK. This includes new TypeScript types for the remote tool registration API, a `ToolCallbackHandler` class that hosts an HTTP endpoint for receiving Java backend tool invocations, a `registerRemoteTool()` method on `SpecDrivenClient`, and unit tests.

## Why

M21 requires tool registration and invocation before the SDK satisfies its done criteria. The two prior changes (`ts-sdk-client`, `ts-sdk-agent`) established the HTTP transport and agent lifecycle wrapper. Tool support is the next capability layer — without it, TS callers cannot expose custom tools to running agents.

## Scope

- New `ToolHandler` callback type: `(parameters: Record<string, unknown>) => Promise<string>`
- New TypeScript models: `RemoteToolRegistrationRequest`, `RemoteToolInvocationRequest`, `RemoteToolInvocationResponse`, `ToolRegistrationResult`
- New `ToolCallbackHandler` class in `sdk/ts/src/tools.ts`:
  - Maintains a registry of named `ToolHandler` functions
  - Exposes a `handleRequest(req, res)` method compatible with Node.js `http.IncomingMessage` / `http.ServerResponse`
  - Dispatches POST requests from the Java backend to the matching registered handler
  - Returns `RemoteToolInvocationResponse` JSON to the backend
- New `registerRemoteTool(request: RemoteToolRegistrationRequest)` method on `SpecDrivenClient` calling `POST /api/v1/tools/register`
- Export all new public types and the `ToolCallbackHandler` class from `sdk/ts/src/index.ts`
- Unit tests in `sdk/ts/src/tools.test.ts`

## Unchanged Behavior

- `SpecDrivenClient` constructor, all existing methods, and authentication headers are unchanged
- `SpecDrivenAgent` run/stop/state operations are unchanged
- `listTools()` behavior is unchanged
- `pollEvents()` behavior is unchanged
- All existing models, errors, and retry logic are unchanged
