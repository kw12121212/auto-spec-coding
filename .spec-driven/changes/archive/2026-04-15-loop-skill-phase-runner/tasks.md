# Tasks: loop-skill-phase-runner

## Implementation

- [x] Add `PhaseExecutionResult` and `SpecDrivenPhaseRunner` contracts for single-phase execution
- [x] Add `CommandSpecDrivenPhaseRunner` with default and configurable command mappings
- [x] Refactor `SpecDrivenPipeline` to delegate phase execution through the runner while preserving existing prompt-backed constructors
- [x] Update delta specs to match the implemented runner behavior and mappings

## Testing

- [x] Run validation command `mvn -DskipTests compile`
- [x] Run unit test command `mvn test`

## Verification

- [x] Run spec-driven apply summary for `loop-skill-phase-runner`
- [x] Run spec-driven verify for `loop-skill-phase-runner`
- [x] Run unmapped spec evidence audit for changed implementation and test files
- [x] Review changed files for code quality and unchanged behavior
