# Design: service-schema-bootstrap-governance

## Approach

- Define startup governance as a narrow extension of the completed M36 bootstrap and runtime-packaging contracts rather than as a new runtime subsystem.
- Express the new behavior in observable terms: which startup input is accepted, when validation must happen, which runtime settings remain externally supplied, and how failures are reported.
- Keep governance centered on automatic-start safety boundaries so later milestones can build on the same runtime surface without expanding this change into deployment or workflow orchestration.

## Key Decisions

- Validate the full `services.sql` input before executing any bootstrap-managed statement.
  Rationale: this prevents partially applied startup state when a later statement or directive is unsupported.
- Keep automatic bootstrap limited to idempotent `CREATE TABLE IF NOT EXISTS` and `CREATE SERVICE IF NOT EXISTS` declarations.
  Rationale: M36 needs a safe automatic startup path, not a general schema migration engine.
- Keep runtime bind and platform settings outside `services.sql`.
  Rationale: host, port, JDBC URL, compile-cache path, and API-key configuration already belong to the runtime entry path and platform defaults; allowing declarative override inside bootstrap input would blur governance boundaries.
- Preserve the existing SDK/platform and CLI runtime entry paths.
  Rationale: this change is about governing the current startup path, not replacing it with a second startup model.

## Alternatives Considered

- Allow non-idempotent `CREATE TABLE` or `CREATE SERVICE` statements during automatic startup.
  Rejected because automatic startup would then become responsible for unsafe or environment-dependent mutations instead of converging idempotently.
- Introduce runtime directives inside `services.sql` for host, port, or other launcher settings.
  Rejected because the repository already has explicit runtime inputs for those settings, and mixing them into bootstrap content would make startup behavior harder to reason about and validate.
- Defer governance until M25 production install or M37 workflow runtime.
  Rejected because both later milestones benefit from a stable governed startup contract instead of inheriting implicit startup policy from code.
