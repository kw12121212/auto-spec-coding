# Design: go-sdk-tests

## Approach

Add a focused Go integration test file under the existing `go-sdk/specdriven` package.

The default integration tests will compose the SDK facades against one `httptest` server that implements the relevant HTTP API contract for health, agent run/stop/state, tools list/register, callback invocation, and event polling. These tests should verify the SDK surfaces as callers use them together rather than repeating every unit-level edge case already covered by the existing files.

Add a second optional live-backend path in the same package or a clearly named companion file. It will skip unless `SPECDRIVEN_GO_SDK_BASE_URL` is set. When enabled, it will construct a real Go SDK client against the supplied backend URL and run only non-destructive checks that are safe for a running development backend, such as health and tools listing. If authentication is required, the test may use existing SDK auth options from environment variables and skip with a clear message when they are missing.

No Java production changes are planned. If implementation discovers that a declared SDK contract cannot be tested because the backend contract is inconsistent, the change should update the proposal before expanding scope.

## Key Decisions

- Keep `go test ./...` hermetic. The default command should not require a Java process, network access, credentials, or specific local ports.
- Use `httptest` for contract integration coverage. Existing Go SDK tests already use this pattern, and it gives deterministic assertions for request paths, auth headers, JSON payloads, and error responses.
- Gate real-backend tests with `SPECDRIVEN_GO_SDK_BASE_URL`. The roadmap expectation for Java-backend validation is preserved without making every local run depend on external setup.
- Keep optional live-backend tests non-destructive. They should not assume a specific LLM provider, long-running agent behavior, or production data state.
- Prefer adding tests over changing SDK implementation. This planned change exists to close integration coverage after the functional SDK layers are complete.

## Alternatives Considered

- Requiring a running Java backend for all Go tests was ruled out because it would make the default Go test command fragile and environment-dependent.
- Testing only with `httptest` was ruled out because it would not satisfy the roadmap intent that Go SDK integration can be validated against a real Java backend.
- Starting the Java backend from Go tests was ruled out because the repo does not yet expose a stable cross-language test harness for that lifecycle, and it would mix deployment concerns into SDK tests.
- Expanding this change to add new SDK features was ruled out because the M20 functional layers are already represented by earlier planned changes.
