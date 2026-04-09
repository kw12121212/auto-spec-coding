# Design: skill-cli-java

## Approach

Create a dedicated Java CLI package that mirrors the current shared `spec-driven.ts` helper script's externally visible behavior while reusing repository-local file conventions. This is a rewrite of one shared workflow script used by multiple skills, not a separate CLI per skill. The CLI should have a single entrypoint that:

1. Resolves the target repository root and `.spec-driven/` directory
2. Parses the command and positional arguments
3. Dispatches to a command handler with shared helpers for markdown scaffolding, index regeneration, roadmap parsing, and validation
4. Writes either human-readable console output or machine-readable JSON, depending on the command contract
5. Returns process exit codes that distinguish success from validation or usage failures

The first implementation target is parity with the current shared workflow script, not feature expansion. File generation should preserve the same artifact names and directory structure so the rest of the spec-driven workflow continues to work unchanged.

## Key Decisions

- Keep parity scope anchored to the current 12-command helper surface: `propose`, `modify`, `apply`, `verify`, `verify-roadmap`, `roadmap-status`, `archive`, `cancel`, `init`, `run-maintenance`, `migrate`, and `list`
- Treat JSON-producing commands as compatibility-sensitive. `roadmap-status` and `verify-roadmap` should preserve their machine-readable report shape closely enough for downstream workflow use
- Reuse shared Java parsers/helpers for roadmap sections, spec indexes, and change directories instead of embedding command-specific parsing in each handler
- Preserve current `.spec-driven/` artifact formats so existing archived changes, active changes, and roadmap milestones remain valid inputs
- Prefer repo-local invocation through Java/Maven rather than introducing a second external runtime or wrapper dependency

## Alternatives Considered

- Keep the Node.js helper script and document it as a build-time tool. Rejected because M11 explicitly calls for a Java rewrite and full removal of the external Node.js dependency
- Implement only a subset of commands now and defer parity. Rejected because this would still leave the workflow split across Java and the helper script
- Replace the current markdown/file-based workflow with a database-backed or service-only CLI. Rejected because it expands scope beyond parity and would break compatibility with the repository's existing `.spec-driven/` contract
