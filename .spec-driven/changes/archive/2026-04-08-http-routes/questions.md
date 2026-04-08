# Questions: http-routes

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Does Lealone HTTP server support path parameters (e.g. `/api/v1/agent/{id}`)?
  Context: Determines routing implementation strategy
  A: No. Lealone uses standard Servlet URL patterns. Must use wildcard mapping (`/api/v1/*`) with manual `getPathInfo()` parsing.

- [x] Q: API versioning strategy — path-based (`/api/v1/`) vs header-based?
  Context: Affects URL structure and future API evolution
  A: Path-based `/api/v1/` — simpler, matches Lealone's servlet wildcard pattern, conventional for REST APIs.
