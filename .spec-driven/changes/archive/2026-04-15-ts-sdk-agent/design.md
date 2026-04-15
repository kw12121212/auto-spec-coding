# Design: ts-sdk-agent

## Approach

Add a thin `SpecDrivenAgent` class in `sdk/ts/src/agent.ts` that delegates all network calls to an injected `SpecDrivenClient`. The agent class holds no state beyond the client reference and default config — it does not track agent IDs between calls. Agent IDs are returned in `AgentRunResult` and are the caller's responsibility to pass to `stop()` or `getState()`.

A `client.agent(config?)` factory method is added to `SpecDrivenClient` as a convenience entry point, keeping the client as the single top-level surface for SDK consumers.

## Key Decisions

**run() returns a blocking final result, not a stream.** The `POST /api/v1/agent/run` endpoint is synchronous (runs the agent to completion before responding). Exposing `run()` as `Promise<AgentRunResult>` is honest to the underlying HTTP contract. Streaming is deferred to `ts-sdk-events`.

**Agent class does not store agent IDs.** A single `SpecDrivenAgent` instance may be reused to run multiple independent prompts. Storing the "current" agent ID would create ambiguous state for concurrent or sequential calls. Callers receive the ID in `AgentRunResult` and pass it explicitly to `stop()` / `getState()`.

**`AgentConfig` is a plain interface, not a class.** Default values (systemPrompt, maxTurns, toolTimeoutSeconds) are optional and merged at call time. No inheritance or builder pattern needed at this scale.

**`client.agent(config?)` factory over `new SpecDrivenAgent(client, config)`.** Hides the constructor, mirrors the Go SDK pattern, and makes the common usage `const agent = client.agent()` concise.

## Alternatives Considered

**Stateful agent with auto-tracked ID** — rejected because it creates ambiguous behavior when `run()` is called multiple times and when stop() is called concurrently.

**`run()` returns an async iterator for streaming** — rejected for this change; the underlying HTTP endpoint is not streaming. Streaming belongs in `ts-sdk-events`.

**Separate `AgentRunner` and `AgentHandle` types** — over-engineering for the current scope; a single class with explicit ID parameters is simpler and sufficient.
