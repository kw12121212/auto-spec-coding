# Design: ts-sdk-client

## Approach

Mirror the Go SDK's directory layout and structural decomposition. Create `sdk/ts/` as a standalone npm package with `package.json` (name `@specdriven/sdk`, private initially), `tsconfig.json` in strict mode, and source files under `sdk/ts/src/`:

- `client.ts` — `SpecDrivenClient` class: constructor accepts `{ baseUrl, auth?, httpClient?, maxRetries? }`, dispatches all HTTP calls using the `fetch` API, injects auth headers, decodes JSON responses, maps errors to `ApiError`
- `models.ts` — TypeScript interfaces matching Java HTTP API payload field names
- `errors.ts` — `ApiError` class extending `Error`: `status`, `code`, `message`, `retryable`
- `retry.ts` — `withRetry(fn, maxRetries)` helper: exponential backoff on retryable errors

Tests live under `sdk/ts/src/` as `*.test.ts`. Each test file uses MSW (`msw/node`) to create an in-process mock HTTP server so all tests are hermetic — no live backend required.

## Key Decisions

**1. Package at `sdk/ts/` (not repo root).**
Keeps the TypeScript package self-contained and mirrors the `go-sdk/` structure. Avoids polluting the repo root with `package.json` and `node_modules/`.

**2. HTTP transport only; JSON-RPC deferred.**
The HTTP REST API is the primary interface for remote/production use and mirrors the Go SDK's transport. JSON-RPC over stdin adds Node.js process-spawning complexity and is a separate use case; deferring it keeps this change focused and shippable.

**3. Node.js built-in `fetch` (Node 18+).**
Node 18 is the current LTS line and ships `fetch` as a global. Avoids adding `node-fetch` or `axios` as runtime dependencies. If `fetch` availability is a concern, it can be injected via the `httpClient` constructor option.

**4. Vitest for unit tests.**
Faster than Jest, native TypeScript support via Vite transforms, first-class ESM compatibility. MSW is the standard in-process mock HTTP layer for `fetch`-based code.

**5. Typed errors with retryable flag.**
Mirrors Go SDK's `ApiError` design. 4xx client errors are non-retryable; 429 and 5xx server errors and network errors are retryable. This lets the `Agent`/`Tools`/`Events` facades (future changes) decide on retry policy without re-implementing error classification.

## Alternatives Considered

**axios instead of fetch:** axios adds a runtime dependency and has its own config model. The `fetch` API is sufficient for straightforward REST calls and is now available natively in Node 18+.

**Jest instead of Vitest:** Jest requires additional transform config for TypeScript + ESM. Vitest has zero-config TypeScript support and is significantly faster in watch mode.

**Repo-root package.json / pnpm workspace:** Unnecessary for a single-package SDK. Adds monorepo tooling overhead. The standalone-directory pattern is simpler and already proven by `go-sdk/`.
