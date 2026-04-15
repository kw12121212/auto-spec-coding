# Questions: interactive-show-audit

## Open

<!-- No open questions -->

## Resolved

- [x] Q: What should `SHOW SERVICES` mean in this project?
  Context: The command already exists but the repository did not yet define whether it should expose Lealone services, project subsystems, or session-scoped capabilities.
  A: `SHOW SERVICES` should report the capabilities and subsystems relevant to the paused human-in-the-loop session, not a generic database service browser.

- [x] Q: How should interactive audit identity be recorded?
  Context: M29 requires auditability for human intervention, but the current interactive contract exposes `sessionId` and not a richer principal.
  A: Use `sessionId` as the mandatory audit attribution field. Richer operator identity may be included later if already available from caller context, but this change must not expand into a new identity model.
