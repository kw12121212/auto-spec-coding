# Questions: service-runtime-packaging

## Open

<!-- No open questions -->

## Resolved

- [x] Q: What should the first supported runtime package entrypoint be?
  Context: M36 requires a minimal runtime packaging/startup contract, but the roadmap does not name the exact operator-facing entrypoint.
  A: Use a Java CLI entrypoint as the primary contract, backed by the existing Maven/JAR project shape. The first supported behavior accepts a `services.sql` path plus minimal runtime config, bootstraps the platform, exposes service HTTP routes, and fails explicitly when required startup material is missing or unsupported.
