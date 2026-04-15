# go-sdk-tests

## What

Add integration coverage for the Go SDK milestone by testing the already implemented Go client, agent, tools, and events surfaces against an HTTP API compatible backend.

The default test path will remain hermetic by using a Go `httptest` server that exercises the Java HTTP API request and response contract. An optional live-backend integration path will run against a real Java backend when the caller provides a backend base URL through environment configuration.

## Why

M20 is almost complete: the Go SDK client, agent, tools, and events planned changes are already complete, and `go-sdk-tests` is the only remaining planned item. Adding integration coverage closes the milestone with confidence that the Go SDK surfaces work together against the backend API contract.

The roadmap calls out integration tests that require the Java backend. Keeping the real-backend path optional avoids making normal `go test ./...` depend on an external service, while still preserving a way to validate the SDK against a running Java server.

## Scope

In scope:

- Add Go integration tests covering the SDK client, agent, tools, and events APIs as combined public workflows.
- Use a hermetic Go `httptest` HTTP API compatible server for the default integration test path.
- Add an optional live-backend integration test path gated by an environment variable such as `SPECDRIVEN_GO_SDK_BASE_URL`.
- Verify authentication headers, JSON request/response compatibility, typed API error preservation, and event polling behavior across SDK facades.
- Keep tests independent and runnable through the Go module test command.

Out of scope:

- Adding new Go SDK features or changing public SDK method signatures.
- Adding TypeScript SDK tests.
- Replacing existing unit tests with only end-to-end tests.
- Starting or managing the Java backend from Go tests.
- Requiring live backend credentials or network access for the default test command.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):

- Existing Go SDK public APIs remain backward compatible.
- Existing Java HTTP REST API routes and payload contracts remain unchanged.
- Existing Go unit tests continue to run without a live Java backend.
- Existing Maven build behavior remains unchanged.
