# Tasks: go-sdk-tests

## Implementation

- [x] Add hermetic Go SDK integration tests that exercise client, agent, tools, and events workflows through one HTTP API compatible `httptest` server.
- [x] Add optional live-backend Go SDK integration tests gated by `SPECDRIVEN_GO_SDK_BASE_URL`.
- [x] Ensure live-backend tests skip clearly when required backend URL or credentials are not provided.
- [x] Keep existing Go SDK public APIs and Java HTTP API behavior unchanged.

## Testing

- [x] Run Go formatting validation: `cd go-sdk && test -z "$(gofmt -l .)"`
- [x] Run Go unit tests and hermetic integration tests: `cd go-sdk && go test ./...`
- [x] Run optional live-backend Go SDK integration tests when a backend is available: `cd go-sdk && SPECDRIVEN_GO_SDK_BASE_URL=<url> go test ./...`
- [x] Run Java compile validation to ensure the proposal did not require Java production changes: `mvn -DskipTests compile`

## Verification

- [x] Verify hermetic integration tests cover the Go SDK client, agent, tools, and events public workflows.
- [x] Verify live-backend tests are skipped by default and do not fail when `SPECDRIVEN_GO_SDK_BASE_URL` is unset.
- [x] Verify no new SDK public API behavior was added outside the proposal scope.
- [x] Run spec-driven verification for this change: `node /home/code/.agents/skills/roadmap-recommend/scripts/spec-driven.js verify go-sdk-tests`
