# ts-sdk-agent

## What

Add a `SpecDrivenAgent` class to the TypeScript SDK that provides a high-level, object-oriented interface for agent lifecycle operations: running a prompt to completion, stopping a running agent, and querying agent state. The class wraps the low-level `SpecDrivenClient` methods already available and exposes a stable, typed surface that SDK consumers can use without managing raw HTTP calls or agent IDs directly.

## Why

`ts-sdk-client` delivered the HTTP transport layer (auth, retry, typed errors, raw endpoint methods). Without an agent abstraction, callers must manage raw `runAgent()` / `stopAgent()` / `getAgentState()` calls and handle agent IDs manually. The agent class completes the minimal usable SDK: a caller should be able to write `const agent = client.agent(config); const result = await agent.run(prompt);` and get a final typed result.

This is also the prerequisite for `ts-sdk-tools` (which registers tools on an agent) and `ts-sdk-events` (which subscribes to events from a running agent).

## Scope

In scope:
- New file `sdk/ts/src/agent.ts` exporting `SpecDrivenAgent` and `AgentConfig`
- `SpecDrivenAgent` is constructed with a `SpecDrivenClient` and an optional `AgentConfig` (systemPrompt, maxTurns, toolTimeoutSeconds)
- Methods: `run(prompt, options?)` → `AgentRunResult`, `stop(agentId)` → `void`, `getState(agentId)` → `AgentStateResponse`
- `AgentRunResult` type: `{ agentId: string; output: string | null; state: string }`
- `client.agent(config?)` factory method on `SpecDrivenClient` returning a `SpecDrivenAgent`
- Update `sdk/ts/src/index.ts` to export new public types
- Unit tests in `sdk/ts/src/agent.test.ts`

Out of scope:
- Streaming / SSE response (deferred to `ts-sdk-events`)
- Tool registration (deferred to `ts-sdk-tools`)
- Agent ID storage / tracking across multiple run calls (caller manages IDs)

## Unchanged Behavior

- `SpecDrivenClient` and all existing transport-layer code are not modified
- Existing exported types (`RunAgentRequest`, `RunAgentResponse`, `AgentStateResponse`, etc.) are unchanged
- Retry, auth, and error handling behavior in `client.ts` are unaffected
