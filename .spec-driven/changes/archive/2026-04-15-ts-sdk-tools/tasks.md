# Tasks: ts-sdk-tools

## Implementation

- [x] Add `RemoteToolRegistrationRequest`, `RemoteToolInvocationRequest`, `RemoteToolInvocationResponse`, and `ToolRegistrationResult` interfaces to `sdk/ts/src/tools.ts`
- [x] Add `ToolHandler` type (`(parameters: Record<string, unknown>) => Promise<string>`) to `sdk/ts/src/tools.ts`
- [x] Implement `ToolCallbackHandler` class in `sdk/ts/src/tools.ts`:
  - `register(name: string, handler: ToolHandler): void` — validates and stores handler
  - `handleRequest(req: IncomingMessage, res: ServerResponse): Promise<void>` — decodes POST body, dispatches to handler, writes JSON response
- [x] Add `registerRemoteTool(request: RemoteToolRegistrationRequest): Promise<ToolRegistrationResult>` to `SpecDrivenClient` in `sdk/ts/src/client.ts`, validating name and callbackUrl before sending `POST /api/v1/tools/register`
- [x] Export `ToolCallbackHandler`, `ToolHandler`, `RemoteToolRegistrationRequest`, `RemoteToolInvocationRequest`, `RemoteToolInvocationResponse`, and `ToolRegistrationResult` from `sdk/ts/src/index.ts`

## Testing

- [x] Run lint: `cd sdk/ts && npm run lint`
- [x] Run unit tests: `cd sdk/ts && npm test`
- [x] Write unit tests in `sdk/ts/src/tools.test.ts` covering:
  - `ToolCallbackHandler.register` — rejects empty name, rejects null handler, stores valid handler
  - `ToolCallbackHandler.handleRequest` — dispatches known tool, returns error for unknown tool, returns 405 for non-POST, returns 400 for invalid JSON, propagates handler error as `success: false`
  - `SpecDrivenClient.registerRemoteTool` — sends `POST /api/v1/tools/register` with correct payload, rejects empty name, rejects empty callbackUrl

## Verification

- [x] Verify `ToolCallbackHandler` and all new types are exported from `index.ts`
- [x] Verify `registerRemoteTool` uses the same auth and retry infrastructure as other `SpecDrivenClient` methods
- [x] Verify no existing tests are broken after the change
- [x] Verify delta spec scenarios are covered by tests
