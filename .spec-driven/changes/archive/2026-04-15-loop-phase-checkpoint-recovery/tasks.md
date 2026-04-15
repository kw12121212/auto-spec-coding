# Tasks: loop-phase-checkpoint-recovery

## Implementation

- [x] Extend the autonomous loop spec with phase checkpoint and recovery requirements.
- [x] Add a loop progress checkpoint contract for the active candidate and completed phases.
- [x] Persist and load checkpoint state in the loop iteration store without breaking old snapshots.
- [x] Update loop driver resume behavior to continue a checkpointed candidate before selecting new work.
- [x] Clear the checkpoint only after the checkpointed iteration reaches successful completion.

## Testing

- [x] Run validation command `mvn -DskipTests compile`.
- [x] Run focused unit test command `mvn test -Dtest=LoopProgressTest,LealoneLoopIterationStoreTest,DefaultLoopDriverTest,SpecDrivenPipelineTest`.
- [x] Run full unit test command `mvn test`.

## Verification

- [x] Run spec-driven verify for `loop-phase-checkpoint-recovery`.
- [x] Review the delta spec, implementation, and tests for alignment with M35 checkpoint recovery scope.
