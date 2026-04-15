# Tasks: go-sdk-tools

## Implementation

- [x] Add Java HTTP API request/response models for callback-backed remote tool registration.
- [x] Add `POST /api/v1/tools/register` routing and validation to `HttpApiServlet`.
- [x] Add backend support for exposing registered remote tools through existing `GET /api/v1/tools`.
- [x] Add backend remote tool execution behavior that calls the registered callback URL during normal agent tool execution.
- [x] Add Go SDK tool definition, parameter, callback invocation, and callback response models.
- [x] Add a Go SDK tools facade constructed from an existing `Client`.
- [x] Add Go SDK remote tool registration behavior that calls `POST /api/v1/tools/register`.
- [x] Add a Go SDK HTTP callback handler that dispatches invocation requests to registered Go tool handlers.
- [x] Preserve existing `Client`, `Agent`, Java SDK, and HTTP API behavior outside the new tools contract.

## Testing

- [x] Add Java unit tests for remote tool registration route validation, duplicate/built-in name handling, and `GET /tools` visibility.
- [x] Add Java unit tests for callback-backed remote tool success and callback failure during agent tool execution.
- [x] Add Go unit tests for tools facade construction, registration request path/body/auth behavior, and API error propagation.
- [x] Add Go unit tests for callback handler success, unknown tool, invalid request, handler error, and request context cancellation.
- [x] Run Go formatting validation: `cd go-sdk && test -z "$(gofmt -l .)"`
- [x] Run Go unit tests: `cd go-sdk && go test ./...`
- [x] Run Java unit tests for affected HTTP/SDK behavior: `mvn -Dtest=HttpApiServletTest,SdkAgentTest test`
- [x] Run Java compile validation: `mvn -DskipTests compile`

## Verification

- [x] Verify the implementation matches the delta spec requirements.
- [x] Verify remote tool invocation still flows through normal agent tool execution rather than a direct arbitrary tool execution endpoint.
- [x] Verify existing `go-sdk-client` and `go-sdk-agent` behavior remains backward compatible.
- [x] Run spec-driven verification for this change: `node /home/wx766/.agents/skills/roadmap-recommend/scripts/spec-driven.js verify go-sdk-tools`
