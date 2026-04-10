# Design: context-lifecycle-manager

## Approach

Reuse the existing `ContextWindowManager` and `TokenCounter` utilities already in `org.specdriven.agent.agent`. After each iteration completes, `DefaultLoopDriver` will accumulate token usage from the pipeline's `IterationResult` into a `ContextWindowManager`. When remaining capacity falls below the configured threshold, the driver saves a final progress snapshot with context exhaustion metadata and transitions to STOPPED with reason "context exhausted".

The restart signaling is event-driven: the `LOOP_CONTEXT_EXHAUSTED` event carries enough metadata for an external orchestrator (or the future M26 context lifecycle restart logic) to spin up a new session and call `start()` to resume from the persisted checkpoint.

### Flow

1. `DefaultLoopDriver.start()` creates a `ContextWindowManager` from `LoopConfig.contextBudget()` if present
2. After each iteration, token usage from `IterationResult` is added to the `ContextWindowManager`
3. If `remainingCapacity()` falls below threshold, driver saves progress with context metadata, publishes `LOOP_CONTEXT_EXHAUSTED`, and stops
4. On next `start()`, persisted `LoopProgress` (now including context usage info) is loaded, and the loop resumes from the correct iteration offset

## Key Decisions

1. **Threshold-based, not hard-limit**: Stop when remaining capacity drops below a percentage (default 20%) rather than waiting for the hard limit. This leaves headroom for the final iteration's output and avoids provider-side truncation.
2. **Accumulate from IterationResult rather than measuring externally**: The pipeline already tracks per-iteration token counts. Summing these is simpler and more accurate than wrapping every LLM call.
3. **Extending LoopProgress rather than a new table**: Adding an optional `contextUsedTokens` field to `LoopProgress` is simpler than a new persistence table and keeps the checkpoint self-contained.
4. **Event-driven restart signal**: Rather than embedding session-restart logic inside the driver, emit an event and let the caller decide how to restart. This keeps the driver agnostic to the execution environment (CLI, daemon, SDK).
5. **Context budget is optional**: When null, no context checking occurs — full backward compatibility with existing callers.

## Alternatives Considered

1. **Pre-flight token estimation before each iteration**: Rejected — estimating token cost of an entire iteration (propose → implement → verify → review → archive) is unreliable because tool call chains are unpredictable.
2. **Automatic session restart inside the driver**: Rejected — managing process/session lifecycle is outside the driver's responsibility. The driver should signal exhaustion and persist state; session management belongs to the caller.
3. **Separate ContextLifecycleStore interface**: Rejected — the existing `LoopIterationStore.saveProgress()` already provides the right persistence hook. Extending `LoopProgress` avoids a new interface.
