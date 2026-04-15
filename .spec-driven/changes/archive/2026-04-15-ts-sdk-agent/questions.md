# Questions: ts-sdk-agent

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Should `run()` return a streaming response or a blocking final result?
  Context: Determines the return type of `agent.run()` and whether streaming infrastructure needs to be scaffolded in this change.
  A: Blocking final result (`Promise<AgentRunResult>`). Streaming is deferred to `ts-sdk-events`.
