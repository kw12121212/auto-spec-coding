# Design: dynamic-llm-config-snapshots

## Approach

Add a minimal runtime configuration layer beside the current static `LlmConfig` model.

The change should define:
- an immutable snapshot object representing the effective non-sensitive LLM settings used for requests
- a registry or resolver contract that returns the currently active snapshot for a given scope, starting with a default scope and session-scoped override behavior
- atomic snapshot replacement semantics so future reads see the replacement as a single switch, never a partially updated config
- request binding semantics so an `LlmClient` request observes exactly one snapshot from request start to request completion

The delta spec should stay at observable behavior level and avoid overcommitting to a specific implementation class layout. Mapping frontmatter can still point to the likely LLM config and registry classes that will carry the implementation.

## Key Decisions

- Choose snapshot semantics before persistence.
  This lets later changes persist a well-defined value instead of persisting mutable in-memory state.

- Limit the first increment to non-sensitive fields.
  Secret handling is explicitly deferred to M33, which already owns vault integration and redaction requirements.

- Require in-flight request stability.
  Dynamic config updates are only safe if a request cannot observe half-old and half-new settings during one call.

- Keep scope centered on default and session-level behavior.
  The milestone mentions per-session and per-agent isolation, but this first change should define the baseline observable contract that later handlers can build on.

## Alternatives Considered

- Start with `set-llm-sql-handler` first.
  Rejected because the SQL entry point would be forced to invent update semantics before the underlying snapshot contract exists.

- Bundle snapshots, persistence, and events into one proposal.
  Rejected because it would blur multiple roadmap items and make later changes harder to verify independently.

- Mutate provider config objects in place.
  Rejected because partial updates and shared mutable state would make concurrent behavior difficult to specify and test.
