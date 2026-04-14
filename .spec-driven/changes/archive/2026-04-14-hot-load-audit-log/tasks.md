# Tasks: hot-load-audit-log

## Implementation

- [x] Update the `event-system` delta spec with the hot-load audit event type and metadata constraints
- [x] Update the `skill-hot-loader` delta spec with audit-event requirements for `load`, `replace`, and `unload`
- [x] Add the hot-load audit event type to the public event enum
- [x] Add optional audit event publication to `LealoneSkillHotLoader` without changing existing permission, trust, cache, registry, or class-loader behavior
- [x] Ensure audit metadata never records raw Java source and includes requester information only when available
- [x] Preserve existing constructor compatibility while supporting audited production construction paths

## Testing

- [x] Add unit tests for successful `load`, `replace`, and `unload` audit events
- [x] Add unit tests for disabled activation, permission rejection, trusted-source rejection, compile diagnostics failure, and infrastructure failure audit events
- [x] Add event-system coverage for the new hot-load audit event type
- [x] Run validation build with `mvn -q -DskipTests compile`
- [x] Run unit tests with `mvn -q -Dtest=SkillHotLoaderTest,EventSystemTest test`

## Verification

- [x] Run `node /home/wx766/.agents/skills/roadmap-recommend/scripts/spec-driven.js verify hot-load-audit-log`
- [x] Verify implementation matches the proposal scope and keeps broader logging, sandboxing, and code-signing work out of scope
