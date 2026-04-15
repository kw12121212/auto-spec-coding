# Tasks: go-sdk-client

## Implementation

- [x] Add `go-sdk` Go module with package `specdriven` and module path `github.com/kw12121212/auto-spec-coding/go-sdk`.
- [x] Implement client configuration for base URL, HTTP client, timeout, auth header mode, retry count, retry wait, and user agent.
- [x] Implement request and response models for health, tools, agent run, agent state, and API errors.
- [x] Implement endpoint methods for health, tools, run agent, stop agent, and get agent state.
- [x] Implement typed Go errors that preserve HTTP status, API error code, message, and retryable status.
- [x] Implement retry behavior for network errors, HTTP 429, and HTTP 5xx without retrying non-retryable 4xx responses.

## Testing

- [x] Run Go formatting validation: `cd go-sdk && test -z "$(gofmt -l .)"`
- [x] Run Go unit tests: `cd go-sdk && go test ./...`
- [x] Run Java compile validation to ensure the new module does not break the existing Maven project: `mvn -DskipTests compile`

## Verification

- [x] Verify the implementation matches the delta spec requirements.
- [x] Verify the change stays limited to `go-sdk-client` scope and does not implement `go-sdk-agent`, `go-sdk-tools`, `go-sdk-events`, or `go-sdk-tests`.
- [x] Run spec-driven verification for this change: `node /home/wx766/.agents/skills/roadmap-recommend/scripts/spec-driven.js verify go-sdk-client`
