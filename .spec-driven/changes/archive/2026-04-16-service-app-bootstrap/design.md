# Design: service-app-bootstrap

## Approach

- Treat `services.sql` as the only supported declarative application bootstrap entry for this first M36 change.
- Define bootstrap as a platform-backed flow that uses the already assembled `LealonePlatform` and effective `PlatformConfig`, rather than introducing a separate application runtime builder with duplicate defaults.
- Specify bootstrap in observable terms: supported entry shape, repeated-start idempotence, failure behavior for unsupported or invalid startup inputs, and coexistence with the existing SDK and agent-facing APIs.
- Keep the contract intentionally narrow so later M36 planned changes can extend it without undoing the initial startup model.

## Key Decisions

- Restrict the first supported entry to `services.sql`.
  Rationale: the milestone allows equivalent declarative material later, but the repository currently has repository evidence only for SQL-centered service registration paths.
- Keep bootstrap separate from application HTTP exposure.
  Rationale: `service-http-exposure` is already a distinct planned change, and mixing it here would blur milestone boundaries.
- Route bootstrap through platform-backed assembly.
  Rationale: M32 already established `LealonePlatform` and `PlatformConfig` as the stable Lealone runtime surface, so M36 should build on that instead of reintroducing parallel startup wiring.
- Limit the safety contract to supported, idempotent bootstrap inputs and explicit failure boundaries.
  Rationale: this gives `service-schema-bootstrap-governance` room to add stricter governance later without requiring this first change to solve all policy questions.

## Alternatives Considered

- Define both `services.sql` and a second declarative bootstrap format now.
  Rejected because the repository does not yet show a second proven application entry model, and the milestone notes explicitly prefer proving reuse of one governance model first.
- Include application HTTP exposure in the same change.
  Rejected because M36 already separates bootstrap from exposure, and combining them would make proposal scope too broad.
- Introduce a separate application runtime abstraction outside `LealonePlatform`.
  Rejected because it would overlap with completed M32 platform work and create duplicate initialization responsibilities.
