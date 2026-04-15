---
mapping:
  implementation:
    - sdk/ts/src/agent.ts
    - sdk/ts/src/index.ts
    - sdk/ts/src/client.ts
  tests:
    - sdk/ts/src/agent.test.ts
---

## ADDED Requirements

### Requirement: TypeScript SDK agent wrapper

The TypeScript SDK MUST provide a `SpecDrivenAgent` class that wraps a `SpecDrivenClient` and exposes agent lifecycle operations through a typed, object-oriented interface.

#### Scenario: run() delegates to runAgent and returns AgentRunResult
- GIVEN a `SpecDrivenAgent` constructed with a client and optional `AgentConfig`
- WHEN the caller invokes `agent.run(prompt)`
- THEN the agent MUST call the client's `runAgent()` with the prompt merged with config defaults
- AND return an `AgentRunResult` containing `agentId`, `output`, and `state`

#### Scenario: run() forwards AgentConfig defaults in the request
- GIVEN an `AgentConfig` with `systemPrompt`, `maxTurns`, and `toolTimeoutSeconds`
- WHEN the caller invokes `agent.run(prompt)` without per-call overrides
- THEN the request sent to `runAgent()` MUST include those config values

#### Scenario: run() per-call options override AgentConfig defaults
- GIVEN an `AgentConfig` with default values and a `run()` call with per-call options
- WHEN the caller invokes `agent.run(prompt, options)`
- THEN the per-call options MUST take precedence over the `AgentConfig` defaults in the request

#### Scenario: stop() delegates agent ID to stopAgent
- GIVEN a `SpecDrivenAgent` and an agent ID returned from a prior `run()` call
- WHEN the caller invokes `agent.stop(agentId)`
- THEN the agent MUST call `client.stopAgent(agentId)` and return `void`

#### Scenario: getState() delegates agent ID to getAgentState
- GIVEN a `SpecDrivenAgent` and an agent ID
- WHEN the caller invokes `agent.getState(agentId)`
- THEN the agent MUST call `client.getAgentState(agentId)` and return the `AgentStateResponse`

### Requirement: TypeScript SDK agent factory method

The `SpecDrivenClient` MUST expose an `agent(config?)` factory method that returns a `SpecDrivenAgent` bound to the client.

#### Scenario: client.agent() returns bound SpecDrivenAgent
- GIVEN a constructed `SpecDrivenClient`
- WHEN the caller invokes `client.agent()` or `client.agent(config)`
- THEN the returned `SpecDrivenAgent` MUST use that client for all subsequent operations
