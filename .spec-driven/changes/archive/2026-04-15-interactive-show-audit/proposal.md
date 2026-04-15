# interactive-show-audit

## What

Complete the last remaining M29 interactive command follow-up by replacing the current placeholder `SHOW SERVICES`, `SHOW STATUS`, and `SHOW ROADMAP` outputs with real interactive summaries, and by recording interactive command activity in the audit log.

The change keeps the existing command grammar and interactive session boundary intact. It focuses on making the existing commands observably useful during a paused human-in-the-loop session and making those human interventions auditable through the existing event and audit infrastructure.

## Why

M29 is almost complete: the interactive session contract, Lealone adapter, loop bridge, and command parser already exist, but `InteractiveCommandHandler` still returns placeholder strings for all `SHOW` commands. That leaves the human-in-the-loop path technically wired but not operationally informative.

The milestone also requires each human intervention to be auditable. The repository already has `EventBus`, `EventType`, and `AuditLogStore`, so the remaining work is to define and route interactive command audit events instead of introducing a second audit subsystem.

Finishing this change closes the remaining planned work in M29 and removes one dependency blocker before later platform-unification work that depends on the interactive capability being complete.

## Scope

- In scope:
  - Replace placeholder `SHOW SERVICES` output with a session-relevant subsystem/service summary rather than a generic database service browser
  - Replace placeholder `SHOW STATUS` output with the current waiting-question and loop-interactive status summary available from existing runtime/event context
  - Replace placeholder `SHOW ROADMAP` output with roadmap progress derived from the existing roadmap files and scheduler-readable milestone metadata
  - Emit dedicated interactive command audit events using the existing `EventBus` / audit-log pipeline
  - Define the minimum audit metadata for interactive commands: `sessionId`, command type/raw input, timestamp, and observable scope/outcome
  - Add unit tests for command output formatting and audit event emission
- Out of scope:
  - New authentication or operator identity models beyond the already-available `sessionId`
  - A generic Lealone SQL service explorer or admin console
  - New remote HTTP or JSON-RPC surfaces for interactive inspection
  - Changes to loop pause/resume control semantics
  - Broad roadmap editing or scheduling behavior changes

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):

- `InteractiveSession` lifecycle methods and command grammar remain unchanged
- `QuestionRuntime.submitAnswer()` remains the only answer-submission path for interactive replies
- `DefaultLoopDriver` pause, interactive entry/exit, and resume behavior remain unchanged
- Existing audit log storage mechanics remain unchanged; this change only adds new event usage and metadata
