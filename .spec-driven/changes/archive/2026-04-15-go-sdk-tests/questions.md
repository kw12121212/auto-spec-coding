# Questions: go-sdk-tests

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Should `go-sdk-tests` require a real running Java backend, or should it use a Go `httptest` server that mimics the Java HTTP API contract?
  Context: The roadmap line says integration tests need the Java backend, but the default repository test command should remain practical in automated and local runs.
  A: Use both layers: hermetic Go `httptest` contract tests as the default `go test ./...` path, plus optional real-backend integration tests gated by environment configuration such as `SPECDRIVEN_GO_SDK_BASE_URL`.
