# Tasks: interactive-session-interface

## Implementation

- [x] Add `InteractiveSession` contract under `org.specdriven.agent.interactive` with lifecycle, input submission, ordered output draining, state query, and close semantics
- [x] Add `InteractiveSessionState` contract defining the observable session lifecycle states used by later adapter and bridge work
- [x] Keep the new contract independent from concrete Lealone client types, `DefaultLoopDriver`, and `QuestionDeliveryService` runtime behavior
- [x] Add delta spec file(s) for the new interactive-session contract with implementation/test mapping frontmatter

## Testing

- [x] Validation command: run `mvn -q compile` and verify the new interactive contract types compile cleanly
- [x] Unit tests command: run `mvn -q test` and verify the interactive-session contract tests and affected loop/question tests pass
- [x] Add unit tests for `InteractiveSession` lifecycle transitions: start, submit, drain output, and close
- [x] Add unit tests for invalid-state and invalid-input behavior: submit-before-start, blank input rejection, and repeated close handling

## Verification

- [x] Verify the final implementation matches the proposal and does not expand into `lealone-agent-adapter`, `loop-question-interactive-bridge`, or `interactive-command-parser`
- [x] Run `node /home/code/.agents/skills/roadmap-recommend/scripts/spec-driven.js verify interactive-session-interface`
