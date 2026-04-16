# Questions: service-app-bootstrap

## Open

<!-- No open questions -->

## Resolved

- [x] Q: What declarative application entry is supported in the first bootstrap change?
  Context: The proposal must decide whether to scope bootstrap to `services.sql` or introduce multiple equivalent entry forms immediately.
  A: The first change supports `services.sql` only.
- [x] Q: Does this change include application HTTP exposure?
  Context: M36 separately plans `service-http-exposure`, so the bootstrap proposal needs a strict boundary.
  A: No. `service-app-bootstrap` covers startup and assembly only, not application HTTP exposure.
- [x] Q: Which runtime surface should own the first bootstrap entry path?
  Context: The proposal needs to decide whether bootstrap is platform-backed or introduced as a parallel runtime abstraction.
  A: The first bootstrap contract is platform-backed and integrates through the existing SDK/platform assembly surface.
