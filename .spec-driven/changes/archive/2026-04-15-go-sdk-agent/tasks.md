# Tasks: go-sdk-agent

## Implementation

- [x] Add an Agent facade in `go-sdk/specdriven` that is constructed from an existing `Client`.
- [x] Add run option types or helpers that map prompt, system prompt, max turns, and tool timeout seconds to `RunAgentRequest`.
- [x] Implement Agent run behavior by delegating to `Client.RunAgent` without duplicating HTTP request, auth, retry, or error handling logic.
- [x] Implement Agent stop behavior by delegating to `Client.StopAgent`.
- [x] Implement Agent state behavior by delegating to `Client.GetAgentState`.
- [x] Preserve existing public `Client` endpoint methods without breaking callers.

## Testing

- [x] Add Go unit tests for Agent construction, nil client rejection, run request mapping, stop/state delegation, error propagation, and context cancellation.
- [x] Run Go formatting validation: `cd go-sdk && test -z "$(gofmt -l .)"`
- [x] Run Go unit tests: `cd go-sdk && go test ./...`
- [x] Run Java compile validation to ensure the Go SDK change does not break the existing Maven project: `mvn -DskipTests compile`

## Verification

- [x] Verify the implementation matches the delta spec requirements.
- [x] Verify the change stays limited to `go-sdk-agent` scope and does not implement `go-sdk-tools`, `go-sdk-events`, or `go-sdk-tests`.
- [x] Run spec-driven verification for this change: `node /home/wx766/.agents/skills/roadmap-recommend/scripts/spec-driven.js verify go-sdk-agent`
