# Tasks: ts-sdk-agent

## Implementation

- [x] Create `sdk/ts/src/agent.ts` with `AgentConfig` interface, `AgentRunResult` type, and `SpecDrivenAgent` class
- [x] Implement `SpecDrivenAgent.run(prompt, options?)` → `Promise<AgentRunResult>` delegating to `client.runAgent()`
- [x] Implement `SpecDrivenAgent.stop(agentId)` → `Promise<void>` delegating to `client.stopAgent()`
- [x] Implement `SpecDrivenAgent.getState(agentId)` → `Promise<AgentStateResponse>` delegating to `client.getAgentState()`
- [x] Add `agent(config?: AgentConfig): SpecDrivenAgent` factory method to `SpecDrivenClient`
- [x] Export `SpecDrivenAgent`, `AgentConfig`, `AgentRunResult` from `sdk/ts/src/index.ts`

## Testing

- [x] Run lint: `cd sdk/ts && npm run lint`
- [x] Run type-check: `cd sdk/ts && npm run typecheck`
- [x] Run unit tests: `cd sdk/ts && npm test`
- [x] Write `sdk/ts/src/agent.test.ts` covering:
  - `run()` delegates correct request to client and returns mapped `AgentRunResult`
  - `stop()` delegates to `client.stopAgent()` with the provided agent ID
  - `getState()` delegates to `client.getAgentState()` with the provided agent ID
  - `AgentConfig` defaults (systemPrompt, maxTurns, toolTimeoutSeconds) are forwarded in the run request
  - `client.agent()` factory returns a `SpecDrivenAgent` bound to the client

## Verification

- [x] All tasks above marked complete
- [x] `sdk/ts/src/index.ts` exports `SpecDrivenAgent`, `AgentConfig`, `AgentRunResult`
- [x] No modifications to existing `client.ts`, `models.ts`, `errors.ts`, or `retry.ts` logic
