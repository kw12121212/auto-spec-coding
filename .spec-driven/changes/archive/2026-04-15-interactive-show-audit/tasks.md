# Tasks: interactive-show-audit

## Implementation

- [x] Extend `InteractiveCommandHandler` to replace placeholder `SHOW SERVICES`, `SHOW STATUS`, and `SHOW ROADMAP` output with real summaries backed by existing runtime and roadmap context
- [x] Add minimal helper logic needed to read roadmap progress and format paused-session service/status summaries without changing interactive command grammar or loop control behavior
- [x] Add interactive command audit event emission with stable metadata based on `sessionId`, command content/type, and observable outcome
- [x] Update or add any supporting event types / event helper utilities required for interactive command auditing

## Testing

- [x] Run validation command `mvn -q -DskipTests compile`
- [x] Run focused unit test command `mvn -q -Dtest=InteractiveCommandHandlerTest,DefaultLoopDriverTest,EventSystemTest,SequentialMilestoneSchedulerTest test`
- [x] Run full unit test command `mvn test -q -Dsurefire.useFile=false`

## Verification

- [x] Verify `SHOW SERVICES` reports paused-session-relevant capabilities instead of placeholder text
- [x] Verify `SHOW STATUS` reflects waiting-question / interactive-loop state without changing loop semantics
- [x] Verify `SHOW ROADMAP` reflects on-disk roadmap progress using the same milestone/planned-change structure used by scheduling logic
- [x] Verify interactive command handling emits audit events that can be persisted through the existing audit log pipeline
- [x] Verify interactive answer submission and session lifecycle behavior remain unchanged
