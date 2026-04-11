# Tasks: loop-escalation-gate

## Implementation

- [x] Add loop-level escalation decision behavior for questions whose `category` requires human approval or whose `deliveryMode` is not `AUTO_AI_REPLY`
- [x] Update `DefaultLoopDriver` QUESTIONING handling so human-only questions bypass `LoopAnswerAgent` and transition `QUESTIONING → PAUSED`
- [x] Ensure `LOOP_QUESTION_ESCALATED` metadata includes `questionId`, `sessionId`, `changeName`, `category`, `deliveryMode`, `reason`, and `routingReason`
- [x] Preserve resume correctness by recording a partial `LoopIteration` with `status=QUESTIONING` without adding the paused change to `completedChangeNames`
- [x] Persist loop progress after escalation when a `LoopIterationStore` is configured
- [x] Integrate escalation notification with existing question delivery behavior when a delivery service is configured, while preserving event-only observability when it is not
- [x] Update delta specs under `changes/loop-escalation-gate/specs/` to match the implemented behavior

## Testing

- [x] Validation command: run `mvn compile -pl . -q` and verify zero compilation errors
- [x] Unit tests command: run `mvn test -pl . -Dtest="org.specdriven.agent.loop.*Test,org.specdriven.agent.question.*Test" -q` and verify all pass
- [x] Add loop driver unit tests for `PERMISSION_CONFIRMATION` and `IRREVERSIBLE_APPROVAL` questions proving `LoopAnswerAgent` is not invoked
- [x] Add loop driver unit test for `PUSH_MOBILE_WAIT_HUMAN` / `PAUSE_WAIT_HUMAN` delivery modes proving the loop pauses and emits escalation metadata
- [x] Add persistence/resume unit test proving escalated partial iterations do not mark the change complete
- [x] Add event metadata unit test for `LOOP_QUESTION_ESCALATED`

## Verification

- [x] Verify the final implementation satisfies all delta spec requirements
- [x] Run `node /home/code/.agents/skills/roadmap-recommend/scripts/spec-driven.js verify loop-escalation-gate`
- [x] Confirm no proposal scope was expanded into M29 interactive session behavior
