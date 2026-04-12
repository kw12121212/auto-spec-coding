---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/loop/SpecDrivenPipeline.java
    - src/main/java/org/specdriven/agent/loop/LoopConfig.java
  tests:
    - src/test/java/org/specdriven/agent/loop/SpecDrivenPipelineTest.java
---

# Autonomous Loop Driver

## MODIFIED Requirements

### Requirement: SpecDrivenPipeline smart context integration
`SpecDrivenPipeline` MUST integrate smart context optimization into autonomous loop phase execution when context budgeting is configured.

#### Scenario: Context-budgeted pipeline optimizes LLM messages
- GIVEN a `LoopConfig` with a non-null context budget
- AND a `SpecDrivenPipeline` executing a phase with an LLM client factory
- WHEN the pipeline passes the phase client to `DefaultOrchestrator`
- THEN LLM calls made by the phase MUST use smart context optimization before reaching the underlying provider client

#### Scenario: Pipeline without context budget remains unchanged
- GIVEN a `LoopConfig` with no context budget
- AND a `SpecDrivenPipeline` executing a phase with an LLM client factory
- WHEN the pipeline passes the phase client to `DefaultOrchestrator`
- THEN LLM calls made by the phase MUST reach the underlying provider client with the same message contents as before this change

#### Scenario: Loop token usage still reflects provider responses
- GIVEN a context-budgeted pipeline using smart context optimization
- AND provider responses include token usage
- WHEN pipeline execution completes
- THEN `IterationResult.tokenUsage()` MUST still reflect the token usage reported by provider responses
- AND context-exhaustion behavior MUST continue to use that reported usage

### Requirement: DefaultOrchestrator signature compatibility
Smart context integration MUST NOT require a new public `DefaultOrchestrator.run(...)` signature.

#### Scenario: Existing orchestrator callers still compile
- GIVEN existing code that calls `DefaultOrchestrator.run(AgentContext, LlmClient)`
- WHEN this change is applied
- THEN the call MUST remain valid

#### Scenario: Wrapped client enables optimization
- GIVEN `DefaultOrchestrator.run(AgentContext, LlmClient)` receives a smart-context-wrapped client
- WHEN the orchestrator makes an LLM call
- THEN optimization MUST occur through the supplied `LlmClient`
- AND orchestrator state transitions, tool execution, question handling, and conversation append behavior MUST remain governed by existing orchestrator requirements
