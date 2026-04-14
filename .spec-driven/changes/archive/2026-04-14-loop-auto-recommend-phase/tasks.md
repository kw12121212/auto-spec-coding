# Tasks: loop-auto-recommend-phase

## Implementation

- [x] Extend loop phase ordering so `RECOMMEND` is the first phase before `PROPOSE`.
- [x] Add the recommend phase instruction template resource under `src/main/resources/loop-phases/recommend.txt`.
- [x] Add loop-only no-confirm recommend behavior that selects only eligible roadmap planned changes and exposes the selected `LoopCandidate` to the rest of the iteration.
- [x] Ensure the propose phase uses the candidate selected by the recommend phase without reselecting a different change.
- [x] Preserve manual `/roadmap-recommend` confirmation semantics and existing scheduler constraints.

## Testing

- [x] Run `mvn compile -q` for validation.
- [x] Run `mvn test -Dtest="PipelinePhaseTest,SequentialMilestoneSchedulerTest,DefaultLoopDriverTest,SpecDrivenPipelineTest"` for focused unit tests.
- [x] Add or update JUnit 5 tests covering recommend-first phase ordering, no-confirm candidate selection, skipping completed changes, and unchanged manual recommendation scope.

## Verification

- [x] Verify the autonomous loop phase order is `RECOMMEND -> PROPOSE -> IMPLEMENT -> VERIFY -> REVIEW -> ARCHIVE`.
- [x] Verify only roadmap planned changes are selectable by loop auto recommend.
- [x] Verify completed milestones and completed planned changes remain skipped.
- [x] Verify no proposal artifacts are created by manual `/roadmap-recommend` without explicit confirmation.
