# Autonomous Loop Driver

## ADDED Requirements

### Requirement: ContextBudget record

- MUST be a Java record in `org.specdriven.agent.loop` with fields: `maxTokens` (int), `warningThresholdPercent` (int)
- Compact constructor MUST reject `maxTokens` ≤ 0 with `IllegalArgumentException`
- `warningThresholdPercent` MUST be between 1 and 99 inclusive; compact constructor MUST reject out-of-range values with `IllegalArgumentException`
- Default `warningThresholdPercent` MUST be 20 (i.e., trigger exhaustion when less than 20% context remains)
- MUST provide static factory `of(int maxTokens)` returning a ContextBudget with default threshold
- MUST provide static factory `of(int maxTokens, int warningThresholdPercent)` returning a ContextBudget with specified threshold

### Requirement: LoopConfig contextBudget field

- `LoopConfig` MUST add an optional field `contextBudget` (ContextBudget, nullable)
- When `contextBudget` is null, no context tracking occurs — the loop behaves exactly as before
- Existing constructors and `defaults()` factory MUST remain backward-compatible, defaulting `contextBudget` to null
- A new static factory or constructor overload MUST accept `contextBudget`

### Requirement: IterationResult tokenUsage field

- `IterationResult` MUST add a field `tokenUsage` (long)
- `tokenUsage` MUST be non-negative; compact constructor MUST reject negative values with `IllegalArgumentException`
- Default value MUST be 0 for backward compatibility
- `StubLoopPipeline` MUST return `IterationResult` with `tokenUsage=0`

### Requirement: SpecDrivenPipeline token accumulation

- `SpecDrivenPipeline.execute()` MUST accumulate token usage from all `LlmResponse.usage()` values across all pipeline phases
- The returned `IterationResult.tokenUsage` MUST reflect the total tokens consumed during the iteration
- When no LLM calls are made, `tokenUsage` MUST be 0

### Requirement: LoopProgress tokenUsage field

- `LoopProgress` MUST add a field `tokenUsage` (long)
- `tokenUsage` MUST be non-negative; compact constructor MUST reject negative values with `IllegalArgumentException`
- Default value MUST be 0

### Requirement: Context exhaustion detection in DefaultLoopDriver

- When `LoopConfig.contextBudget()` is non-null, `DefaultLoopDriver` MUST create a `ContextWindowManager` with the configured `maxTokens` on `start()`
- After each iteration completes, the driver MUST add the iteration's `IterationResult.tokenUsage()` to the `ContextWindowManager`
- When `ContextWindowManager.remainingCapacity()` falls below `maxTokens * warningThresholdPercent / 100`, the driver MUST:
  1. Save progress via `store.saveProgress()` with current `tokenUsage` in the snapshot
  2. Publish `LOOP_CONTEXT_EXHAUSTED` event with metadata: `tokenUsage` (long), `maxTokens` (int), `remainingTokens` (long), `completedIterations` (int)
  3. Stop the loop with reason "context exhausted"
- When `LoopConfig.contextBudget()` is null, no context checking MUST occur

### Requirement: Context recovery on start

- When `DefaultLoopDriver.start()` recovers progress from the store and the recovered `LoopProgress.tokenUsage()` is greater than 0, the driver MUST initialize the `ContextWindowManager` with the recovered token usage value
- This ensures that consecutive sessions tracking the same context budget accumulate correctly

### Requirement: LOOP_CONTEXT_EXHAUSTED EventType

- MUST add `LOOP_CONTEXT_EXHAUSTED` to the existing `EventType` enum in `org.specdriven.agent.event`
- Existing EventType values MUST NOT change

### Requirement: LealoneLoopIterationStore backward-compatible tokenUsage serialization

- `saveProgress()` MUST serialize the `tokenUsage` field in the JSON progress snapshot
- `loadProgress()` MUST deserialize `tokenUsage` from JSON when present, defaulting to 0 when the key is absent
- This ensures forward compatibility: old snapshots without `tokenUsage` can be loaded by new code
