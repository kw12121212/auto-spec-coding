---
mapping:
  tests:
    - go-sdk/specdriven/integration_test.go
---

## ADDED Requirements

### Requirement: Go SDK integration tests
The Go client SDK MUST include integration tests that exercise the public client, agent, tools, and events workflows against an HTTP API compatible backend.

#### Scenario: Hermetic integration tests run by default
- GIVEN the Go SDK test suite is run without external backend environment variables
- WHEN the caller runs `go test ./...` from the Go SDK module
- THEN the integration tests MUST run against an in-process HTTP API compatible test server
- AND the tests MUST NOT require a live Java backend, network service, or credentials

#### Scenario: Integrated SDK workflows use one backend contract
- GIVEN an HTTP API compatible test server
- WHEN the integration tests exercise health, agent run/state/stop, tools list/register, and event polling through the Go SDK public APIs
- THEN the tests MUST verify the SDK sends the expected HTTP paths, methods, authentication headers, and JSON payloads
- AND the tests MUST verify the SDK decodes successful responses and typed API errors consistently across facades

### Requirement: Optional live backend validation
The Go client SDK MUST provide an optional integration test path for validating the SDK against a running Java HTTP backend.

#### Scenario: Live backend tests skip without configuration
- GIVEN `SPECDRIVEN_GO_SDK_BASE_URL` is unset
- WHEN the Go SDK test suite is run
- THEN live-backend integration tests MUST skip with a clear reason instead of failing

#### Scenario: Live backend tests use configured backend
- GIVEN `SPECDRIVEN_GO_SDK_BASE_URL` points to a running Java HTTP backend
- WHEN live-backend integration tests are run
- THEN the tests MUST construct the Go SDK client with that base URL
- AND verify non-destructive backend-compatible operations such as health and tool listing
