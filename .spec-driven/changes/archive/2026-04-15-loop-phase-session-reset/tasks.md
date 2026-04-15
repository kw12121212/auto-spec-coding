# Tasks: loop-phase-session-reset

## Implementation

- [x] Update the autonomous loop spec with the phase session reset requirements.
- [x] Enforce fresh prompt-backed phase context for every non-skipped phase.
- [x] Enforce independent command-backed phase process execution for every command phase.
- [x] Preserve selected candidate continuity while preventing chat history or session reuse across phases.

## Testing

- [x] Run validation command `mvn -DskipTests compile`.
- [x] Run focused unit test command `mvn test -Dtest=SpecDrivenPipelineTest`.
- [x] Run full unit test command `mvn test`.

## Verification

- [x] Run spec-driven verify for `loop-phase-session-reset`.
- [x] Review changed files for spec alignment and unchanged behavior.
