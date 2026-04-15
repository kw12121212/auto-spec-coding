# Tasks: ts-sdk-tools

## Implementation

- [ ] Add `RemoteToolRegistrationRequest`, `RemoteToolInvocationRequest`, `RemoteToolInvocationResponse`, and `ToolRegistrationResult` interfaces to `sdk/ts/src/tools.ts`
- [ ] Add `ToolHandler` type (`(parameters: Record<string, unknown>) => Promise<string>`) to `sdk/ts/src/tools.ts`
- [ ] Implement `ToolCallbackHandler` class in `sdk/ts/src/tools.ts`:
  - `register(name: string, handler: ToolHandler): void` — validates and stores handler
  - `handleRequest(req: IncomingMessage, res: ServerResponse): Promise<void>` — decodes POST body, dispatches to handler, writes JSON response
- [ ] Add `registerRemoteTool(request: RemoteToolRegistrationRequest): Promise<ToolRegistrationResult>` to `SpecDrivenClient` in `sdk/ts/src/client.ts`, validating name and callbackUrl before sending `POST /api/v1/tools/register`
- [ ] Export `ToolCallbackHandler`, `ToolHandler`, `RemoteToolRegistrationRequest`, `RemoteToolInvocationRequest`, `RemoteToolInvocationResponse`, and `ToolRegistrationResult` from `sdk/ts/src/index.ts`

## Testing

- [ ] Run lint: `cd sdk/ts && npm run lint`
- [ ] Run unit tests: `cd sdk/ts && npm test`
- [ ] Write unit tests in `sdk/ts/src/tools.test.ts` covering:
  - `ToolCallbackHandler.register` — rejects empty name, rejects null handler, stores valid handler
  - `ToolCallbackHandler.handleRequest` — dispatches known tool, returns error for unknown tool, returns 405 for non-POST, returns 400 for invalid JSON, propagates handler error as `success: false`
  - `SpecDrivenClient.registerRemoteTool` — sends `POST /api/v1/tools/register` with correct payload, rejects empty name, rejects empty callbackUrl

## Verification

- [ ] Verify `ToolCallbackHandler` and all new types are exported from `index.ts`
- [ ] Verify `registerRemoteTool` uses the same auth and retry infrastructure as other `SpecDrivenClient` methods
- [ ] Verify no existing tests are broken after the change
- [ ] Verify delta spec scenarios are covered by tests
