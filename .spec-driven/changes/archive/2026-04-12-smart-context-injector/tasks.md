# Tasks: smart-context-injector

## Implementation

- [x] Add `SmartContextInjector` and `SmartContextInjectorConfig` under `src/main/java/org/specdriven/agent/agent/` as an `LlmClient` decorator with deterministic defaults.
- [x] Compose `ToolResultFilter` and `ConversationSummarizer` so filtering runs before summarization and both use `ContextRetentionPolicy` mandatory decisions.
- [x] Preserve all non-message `LlmRequest` parameters and return the delegate `LlmResponse` unchanged.
- [x] Support legacy `chat(List<Message>)` optimization so existing `DefaultOrchestrator` calls can be optimized without changing the orchestrator public signature.
- [x] Add deterministic current-turn metadata handling: explicit metadata when available, latest user-visible message fallback when absent.
- [x] Integrate `SmartContextInjector` into `SpecDrivenPipeline` when `LoopConfig.contextBudget()` is configured, while leaving loops without a context budget unchanged.
- [x] Add a fixed evaluation fixture that reports baseline estimated tokens, optimized estimated tokens, token reduction percentage, and mandatory-context preservation.
- [x] Keep existing provider clients, question routing, answer replay, audit lifecycle, and context-exhaustion behavior backward compatible.

## Testing

- [x] Add `SmartContextInjectorTest` under `src/test/java/org/specdriven/agent/agent/` covering delegate wrapping, composition order, mandatory retention, request parameter preservation, list-based calls, and disabled/no-op behavior.
- [x] Add loop or pipeline focused tests under `src/test/java/org/specdriven/agent/loop/` proving context-budgeted pipeline calls use optimized messages and non-budgeted calls remain unchanged.
- [x] Add fixed evaluation tests proving estimated token reduction is at least 30% and recovery/question/answer/audit markers are preserved.
- [x] Run lint or validation command `mvn -DskipTests compile`.
- [x] Run focused unit test command `mvn -Dtest=SmartContextInjectorTest test`.
- [x] Run full unit test command `mvn test`.

## Verification

- [x] Run proposal validation command `node /home/code/.agents/skills/roadmap-recommend/scripts/spec-driven.js verify smart-context-injector`.
- [x] Confirm the implementation matches the M27 `smart-context-injector` roadmap item and does not start M28-M34 work.
- [x] Confirm the delta specs describe observable behavior and that tests verify behavior rather than private implementation details.
- [x] Confirm M27 can be marked complete after this change is implemented, verified, reviewed, and archived.
