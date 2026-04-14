# Questions: hot-load-default-disabled

## Open

<!-- No open questions -->

## Resolved

- [x] Q: What explicit enable surface should this proposal promise?
  Context: The first M34 change needed a concrete enablement contract before proposal artifacts could be scoped.
  A: Keep this proposal programmatic-only. Explicit enablement comes from the constructing code path, not from a new YAML, SQL, CLI, HTTP, or SDK command surface.
