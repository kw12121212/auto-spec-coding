# Tasks: ts-sdk-client

## Implementation

- [x] Create `sdk/ts/package.json` with name `@specdriven/sdk`, Node.js 18+ engine requirement, TypeScript + Vitest + MSW dev dependencies, `main`/`types` pointing to compiled output, and `build`/`lint`/`test` scripts
- [x] Create `sdk/ts/tsconfig.json` with `strict: true`, `target: ES2022`, `module: NodeNext`, `outDir: dist`, `declaration: true`
- [x] Implement `sdk/ts/src/models.ts`: TypeScript interfaces for `RunAgentRequest`, `RunAgentResponse`, `AgentStateResponse`, `ToolInfo`, `ToolsListResponse`, `HealthResponse`, `ErrorResponse`, `EventPollResponse`, `PollEventsOptions`
- [x] Implement `sdk/ts/src/errors.ts`: `ApiError` class extending `Error` with `status: number`, `code: string`, `retryable: boolean` fields and static factory for network errors
- [x] Implement `sdk/ts/src/retry.ts`: `withRetry<T>(fn: () => Promise<T>, maxRetries: number): Promise<T>` with exponential backoff on retryable `ApiError` and network errors
- [x] Implement `sdk/ts/src/client.ts`: `SpecDrivenClient` class with constructor validating `baseUrl`; auth header injection; `health()`, `listTools()`, `runAgent()`, `stopAgent()`, `getAgentState()`, `pollEvents()` methods; JSON encode/decode; `ApiError` mapping from non-2xx responses
- [x] Add `.eslintrc.json` and `.prettierrc` under `sdk/ts/` for lint and formatting

## Testing

- [x] Type-check: `cd sdk/ts && npx tsc --noEmit`
- [x] Lint: `cd sdk/ts && npx eslint src`
- [x] Run unit tests: `cd sdk/ts && npx vitest run`
- [x] Write `sdk/ts/src/errors.test.ts`: verify `ApiError` fields, `retryable` classification for 4xx/5xx/network errors
- [x] Write `sdk/ts/src/retry.test.ts`: verify retry on 429, retry on 5xx, no retry on 400, budget exhaustion returns last error
- [x] Write `sdk/ts/src/models.test.ts`: verify JSON serialization field names match Java HTTP API contract
- [x] Write `sdk/ts/src/client.test.ts`: use MSW mock server to verify each endpoint method sends correct path, method, auth headers, and decodes response
- [x] Write `sdk/ts/src/integration.test.ts`: end-to-end flows (health → listTools → runAgent → getAgentState → stopAgent → pollEvents) against MSW mock server; verify hermetic (no live backend required)

## Verification

- [x] All `sdk/ts/src/*.test.ts` pass with `npx vitest run` and no live backend
- [x] `tsc --noEmit` reports zero type errors
- [x] ESLint reports zero errors
- [x] Proposal scope is fully implemented: client construction, auth headers, all 6 endpoint methods, typed errors, retry
- [x] No agent, tools, or events facade code is introduced (those are separate changes)
- [x] `go-sdk/` and Java source are untouched
