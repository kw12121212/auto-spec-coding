# Tasks: loop-question-interactive-bridge

## Implementation

- [x] Add `LOOP_INTERACTIVE_ENTERED` and `LOOP_INTERACTIVE_EXITED` to `EventType` enum
- [x] Create `InteractiveSessionFactory` functional interface in `org.specdriven.agent.loop` with `create(String sessionId)` method
- [x] Add `InteractiveSessionFactory` parameter to `DefaultLoopDriver` constructor chain (nullable, defaults to null)
- [x] Implement interactive session creation in the human-escalation pause path: when factory is non-null, call `factory.create(sessionId)`, then `start()`, then publish `LOOP_INTERACTIVE_ENTERED` event
- [x] Implement scheduling thread blocking logic: wait while session state is `ACTIVE`, check for `CLOSED` or `ERROR` transition
- [x] Implement `LOOP_INTERACTIVE_EXITED` event publishing on session close/error
- [x] Handle `stop()` interruption during active interactive session: close session, publish exit event, transition to STOPPED
- [x] Ensure `resume()` after interactive session exit follows existing PAUSED → RECOMMENDING path unchanged

## Testing

- [x] Run `mvn compile -pl . -q` — validate compilation with no errors
- [x] Run `mvn test -pl . -Dtest=DefaultLoopDriverTest -q` — unit test backward compatibility of existing DefaultLoopDriver tests
- [x] Add unit test: no factory configured → pause behavior identical to pre-change
- [x] Add unit test: factory configured → interactive session created on human escalation
- [x] Add unit test: session closed → loop remains PAUSED, EXITED event published
- [x] Add unit test: session error → loop remains PAUSED, EXITED event published
- [x] Add unit test: stop() during interactive session → session closed, loop STOPPED
- [x] Add unit test: multiple pauses → each creates fresh session instance
- [x] Add unit test: InteractiveSessionFactory.create returns session in NEW state
- [x] Add unit test: LOOP_INTERACTIVE_ENTERED/EXITED events contain correct metadata

## Verification

- [x] Verify implementation matches proposal scope — no InteractiveSession interface changes
- [x] Verify all existing DefaultLoopDriver tests pass without modification
- [x] Verify DefaultLoopDriver constructors without InteractiveSessionFactory compile and behave unchanged
- [x] Verify interactive session lifecycle is bounded to single pause cycle (no cross-pause reuse)
