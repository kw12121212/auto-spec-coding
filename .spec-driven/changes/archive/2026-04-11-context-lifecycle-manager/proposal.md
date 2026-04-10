# context-lifecycle-manager

## What

Add context lifecycle management to the autonomous loop driver: track cumulative token usage across iterations, detect when the context window is approaching exhaustion, save loop state, and signal that a new session should be started to continue execution from the saved checkpoint.

## Why

The M24 autonomous loop driver currently runs until `maxIterations` is reached or no more candidates exist. In long-running scenarios, the LLM context window fills up well before these termination conditions are met. Without context lifecycle management, the loop will either produce degraded output as context fills up, or crash when the context is hard-truncated by the LLM provider. This change is the foundation for M26's recovery capabilities — `loop-answer-agent-integration` and `loop-escalation-gate` both depend on the ability to save and restore loop state across session boundaries.

## Scope

- Define `ContextBudget` record to configure context window limits and threshold percentages
- Extend `LoopConfig` with optional `contextBudget` field and `llmConfig` reference
- Extend `DefaultLoopDriver` to check context usage after each iteration and trigger a graceful context switch (save state → stop → signal restart)
- Add `LOOP_CONTEXT_EXHAUSTED` event type
- Persist context usage data alongside loop progress so recovery can determine the exact continuation point
- Unit tests covering threshold detection, state save on exhaustion, and resume from checkpoint

## Unchanged Behavior

- Existing loop state machine transitions (IDLE → RECOMMENDING → RUNNING → CHECKPOINT → ...) remain unchanged
- `start()`, `pause()`, `resume()`, `stop()` signatures and semantics unchanged
- `SequentialMilestoneScheduler` selection logic unchanged
- `LoopIterationStore` interface unchanged — only `LoopProgress` record gains an optional context field
- When no `contextBudget` is configured, the loop behaves exactly as before
- Existing `DefaultLoopDriver` constructors without context budget remain backward-compatible
