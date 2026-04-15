# ts-sdk-client

## What

Implement a TypeScript HTTP client SDK for the Java backend REST API. This is the transport-layer foundation for the full TypeScript SDK (M21): it provides the base HTTP client class, TypeScript request/response types, authentication header injection, typed errors, retry logic, and a low-level event polling method.

## Why

The Go SDK (M20) was just completed and proven against the existing HTTP API. TypeScript is the dominant language for web tooling, CLI tooling, and API clients. Providing a TypeScript SDK expands the Java backend's reach to the JS/TS ecosystem. `ts-sdk-client` is the prerequisite for all remaining M21 changes (`ts-sdk-agent`, `ts-sdk-tools`, `ts-sdk-events`, `ts-sdk-tests`) — nothing in those changes can be built without the base transport client that this change delivers.

## Scope

**In scope:**
- `sdk/ts/` as a self-contained npm package directory (own `package.json`, `tsconfig.json`)
- TypeScript interfaces and types for all HTTP API request and response payloads (RunAgentRequest, RunAgentResponse, AgentStateResponse, ToolInfo, ToolsListResponse, HealthResponse, ErrorResponse, EventPollResponse)
- Base `SpecDrivenClient` class: base URL construction, `fetch`-based request dispatch, authentication header injection (Bearer and API key), response decoding, error mapping
- Typed `ApiError` class: HTTP status, API error code, message, retryable flag
- Retry: exponential backoff on 429, 5xx, and network errors; configurable max retries (default 3)
- Low-level endpoint methods: `health()`, `listTools()`, `runAgent()`, `stopAgent()`, `getAgentState()`, `pollEvents()`
- Unit tests with in-process mock HTTP server (no live backend required)
- ESLint + Prettier for lint and formatting; build via `tsc`

**Out of scope:**
- JSON-RPC transport (deferred to a future change)
- High-level `Agent`, `Tools`, `Events` facades (separate M21 changes)
- Browser runtime support (Node.js only in this change)
- Publishing to npm registry
- Remote tool registration endpoint (part of `ts-sdk-tools`)
- Remote tool callback handler

## Unchanged Behavior

- Java HTTP API endpoint contracts (`/api/v1/*`) — not modified
- Go SDK module (`go-sdk/`) — not touched
