# Design: permissions-model

## Approach

Introduce a first-class permission decision model at the permission interface boundary, then align the tool-facing specs to consume that model without broadening scope into orchestration or storage.

The change should:
- add a structured permission decision type that can represent `ALLOW`, `DENY`, and `CONFIRM`
- change the permission provider contract from boolean-only evaluation to returning that decision type
- define a default policy provider behavior suitable for the currently implemented tools
- update tool specs so they explicitly stop execution on both `DENY` and `CONFIRM`, returning a structured permission error instead of silently treating all non-allow outcomes the same

The default policy semantics should be minimal and observable:
- bash execution is confirmation-gated by default
- write and edit operations are confirmation-gated by default
- read/search operations are allowed by default within the active working tree and denied outside it

This keeps M6's core model consistent with the roadmap while leaving the user-facing approval workflow to `permissions-hooks` and later interface milestones.

## Key Decisions

1. Introduce the three-state decision now
   M6 already declares allow/deny/confirm as part of the permission model. Adding it in `permissions-model` avoids a second interface break when `permissions-hooks` is implemented.

2. Keep the change at the spec and contract layer
   The goal is to establish the core decision vocabulary and default behavior, not to implement transport-specific prompting or persistence. That preserves clean milestone boundaries.

3. Treat `CONFIRM` as a distinct observable result
   `CONFIRM` is not equivalent to `DENY`. Tools should return an explicit result indicating confirmation is required so later hooks and interface layers can surface the right UX without changing tool semantics again.

4. Define conservative default policy behavior for existing tools
   The current repository already uses permission checks in bash, file operations, grep, and glob. Specifying default outcomes for those categories makes the model actionable immediately and reduces ambiguity for later implementation.

## Alternatives Considered

- Keep `check(...) -> boolean` for now and defer `confirm`
  Rejected because it would force a second contract change in `permissions-hooks` and leaves the core milestone partially underspecified.

- Fold orchestration interception into this change
  Rejected because that is the next planned change in the same milestone (`permissions-hooks`) and would blur milestone-internal boundaries.

- Define DB-backed policy administration now
  Rejected because persistent storage and runtime audit behavior belong to `permission-policy-store`, not the foundational model change.
