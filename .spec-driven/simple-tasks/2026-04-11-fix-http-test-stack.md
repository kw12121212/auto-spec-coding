# Task

Fix the HTTP E2E spec drift where `http-e2e-tests.md` requires an `HttpTestStack`
fixture but the repository only assembled the embedded Tomcat stack inline inside
`HttpE2eTest`.

# What Was Done

- Added `HttpTestStack` as a reusable test fixture that starts embedded Tomcat,
  registers `AuthFilter`, `RateLimitFilter`, and `HttpApiServlet` on `/api/v1/*`,
  and exposes request helpers backed by a real base URL.
- Refactored `HttpE2eTest` to use `HttpTestStack` for the shared full-stack server
  and low-threshold rate-limit servers.
- Added E2E coverage that verifies the test stack starts, serves `/health`, stops,
  and releases its port for rebinding.

# Spec Impact

Mapping drift detected. The behavior now matches the existing
`.spec-driven/specs/api/http-e2e-tests.md` requirement, but the newly added
`src/test/java/org/specdriven/agent/http/HttpTestStack.java` fixture is not yet
listed in that spec's `mapping.tests` frontmatter.

# Follow-up

Run `/spec-driven-resync-code-mapping` for `.spec-driven/specs/api/http-e2e-tests.md`
and add `src/test/java/org/specdriven/agent/http/HttpTestStack.java` to
`mapping.tests`.
