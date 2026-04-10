# Tasks: context-lifecycle-manager

## Implementation

- [x] Create `ContextBudget` record in `org.specdriven.agent.loop` with fields: `maxTokens` (int), `warningThresholdPercent` (int, default 20), `modelName` (String, nullable)
- [x] Add `contextBudget` (ContextBudget, nullable) field to `LoopConfig` record; update compact constructor and `defaults()` factory; ensure null is normalized to "no context tracking"
- [x] Add `tokenUsage` (long, default 0) field to `LoopProgress` record; update compact constructor to accept nullable Long, normalizing null to 0; ensure backward compatibility with existing persisted JSON (missing field → 0)
- [x] Add `tokenUsage` (long, default 0) field to `IterationResult` record; update `StubLoopPipeline` to return 0 for tokenUsage
- [x] Update `SpecDrivenPipeline` to accumulate token usage from LlmResponse usage data across all phases and set it in the returned `IterationResult`
- [x] Add `LOOP_CONTEXT_EXHAUSTED` to `EventType` enum
- [x] Update `DefaultLoopDriver`: create `ContextWindowManager` from config's `contextBudget` on `start()`; after each iteration, add iteration's token usage; check remaining capacity against threshold; on exhaustion save progress with context metadata, publish `LOOP_CONTEXT_EXHAUSTED`, stop with reason "context exhausted"
- [x] Update `LealoneLoopIterationStore` to serialize/deserialize the new `tokenUsage` field in `LoopProgress` JSON (backward-compatible: missing key → 0)
- [x] Create delta spec file `changes/context-lifecycle-manager/specs/autonomous-loop.md` with new requirements

## Testing

- [x] Lint/validation: run `mvn compile -pl . -q` and verify zero compilation errors
- [x] Unit tests: run `mvn test -pl . -Dtest="org.specdriven.agent.loop.*Test" -q` and verify all pass
- [x] Write `ContextBudgetTest` — validates construction, defaults, and rejection of invalid values
- [x] Write context exhaustion integration test in `DefaultLoopDriverTest` — configure a small `ContextBudget`, run iterations with non-zero token usage, verify STOPPED state and `LOOP_CONTEXT_EXHAUSTED` event
- [x] Write context resume test in `DefaultLoopDriverTest` — persist progress with token usage, start new driver instance, verify it resumes with correct completedChangeNames and iteration offset
- [x] Write no-budget backward-compatibility test — verify existing constructors and null budget produce identical behavior to pre-change driver
- [x] Update `LealoneLoopIterationStoreTest` to cover new `tokenUsage` field serialization/deserialization
- [x] Update `LealoneLoopIterationStoreTest` to cover backward-compatible 3-arg constructor

## Verification

- [x] All new and existing tests pass
- [x] No behavioral change when `contextBudget` is null (existing callers unaffected)
- [x] LoopProgress JSON deserialization handles both old format (no tokenUsage key) and new format
