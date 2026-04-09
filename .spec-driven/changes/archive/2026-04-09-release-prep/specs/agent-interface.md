# Agent Interface Spec (Delta: release-prep)

## MODIFIED Requirements

### Requirement: DefaultOrchestrator implementation

- Runs that terminate before any tool execution MUST NOT require successful permission policy store initialization

#### Scenario: Null LLM returns without policy-store initialization
- GIVEN a `DefaultOrchestrator` with no tool execution to perform because `LlmClient` is null
- WHEN `run(AgentContext, LlmClient)` is called
- THEN it MUST return immediately
- AND it MUST NOT fail because permission policy storage was unavailable

#### Scenario: Tool-free response does not depend on policy-store initialization
- GIVEN a `DefaultOrchestrator` whose `LlmClient` returns a `TextResponse` without any tool calls
- WHEN `run(AgentContext, LlmClient)` is called
- THEN it MUST append the assistant text and stop normally
- AND it MUST NOT fail because permission policy storage was unavailable
