# Design: interactive-show-audit

## Approach

Extend `InteractiveCommandHandler` from a pure placeholder dispatcher into a small orchestration point with two additional responsibilities:

1. Resolve real `SHOW` outputs from existing project state.
2. Publish interactive-command audit events that are automatically persisted by the existing `LealoneAuditLogStore` when configured.

The implementation should stay minimal and remain centered in `InteractiveCommandHandler`.

For `SHOW` commands:
- `SHOW SERVICES` should return a human-readable summary of the paused session's relevant capabilities/subsystems, not a generic DB service list. This keeps the output aligned with M29's interactive support scope.
- `SHOW STATUS` should summarize the waiting question and current interactive/loop state that is already observable through the question and loop integration path.
- `SHOW ROADMAP` should read the roadmap files using the same canonical milestone/planned-change structure already consumed by `SequentialMilestoneScheduler`, so the output matches the project's real planning state.

For auditing:
- Add a dedicated interactive command event type instead of inventing a new persistence layer.
- Emit one audit event per handled interactive command with stable metadata fields that satisfy the existing `Event` JSON constraints.
- Use `sessionId` as the required actor attribution. If richer operator identity is unavailable, do not expand the session/auth model in this change.

## Key Decisions

1. Use the existing event/audit pipeline.
Rationale: `EventBus`, `EventType`, and `LealoneAuditLogStore` already provide append-only audit persistence. Reusing them avoids creating a second audit model just for interactive commands.

2. Keep `SHOW SERVICES` scoped to paused-session capabilities.
Rationale: the accepted product decision was to show services/subsystems relevant to the human-in-the-loop session, not to build a broad Lealone service browser.

3. Use `sessionId` as the mandatory identity anchor.
Rationale: M29 wants operator attribution, but the current contract does not expose a stronger identity field. `sessionId` is already stable and observable without expanding scope into authentication design.

4. Keep roadmap inspection file-based.
Rationale: the roadmap is already authoritative on disk and consumed by `SequentialMilestoneScheduler`. Reusing that structure reduces drift between interactive output and scheduler behavior.

5. Prefer additive audit metadata over new mutable state.
Rationale: this change is about visibility and auditability, not about changing loop control or question lifecycle semantics.

## Alternatives Considered

1. Add a new interactive-specific audit store.
Rejected because it duplicates existing event persistence and would fragment audit queries.

2. Record only free-form session output without structured event metadata.
Rejected because it makes tests and downstream audit queries brittle, and it weakens observable behavior.

3. Expand `InteractiveSession` to carry an explicit operator principal.
Rejected for this change because it would widen scope into identity modeling. The accepted decision is to use `sessionId` as the required attribution field for now.

4. Make `SHOW SERVICES` query arbitrary Lealone services.
Rejected because it exceeds the milestone goal of guiding a paused human-in-the-loop session and risks turning this change into a general admin console feature.
