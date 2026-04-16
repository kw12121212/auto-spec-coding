# Design: service-http-exposure

## Approach

- Treat application-service HTTP exposure as a separate route namespace from the existing `/api/v1/*` agent management API.
- Specify the initial route shape as `POST /services/{serviceName}/{methodName}` so callers address one service method per HTTP invocation.
- Use a deliberately small invocation body contract: JSON object with an `args` array for positional method arguments.
- Reuse the existing HTTP authentication/filter boundary for exposed service methods rather than defining a separate anonymous application-service path.
- Keep the contract observable and implementation-neutral: route, request, response, error behavior, auth boundary, and compatibility guarantees.

## Key Decisions

- Use `/services/{serviceName}/{methodName}` for the first service invocation namespace.
  Rationale: this keeps business application service calls visibly separate from `/api/v1/*` agent management routes and avoids overloading existing API semantics.
- Support only `POST` invocation in the first change.
  Rationale: service methods may have side effects, and `POST` avoids prematurely classifying methods into HTTP verbs before the service contract records enough metadata.
- Use `{"args":[...]}` as the first request body shape.
  Rationale: positional arguments map directly to service method invocation while keeping the first contract smaller than mixed positional/named binding.
- Require the existing HTTP auth/filter chain by default.
  Rationale: service exposure is externally callable application behavior and should not bypass the repository's established HTTP security boundary.
- Do not add packaging or schema-governance behavior in this change.
  Rationale: M36 already has separate planned changes for runtime packaging and schema bootstrap governance.

## Alternatives Considered

- Expose application services under `/api/v1/services/...`.
  Rejected because it would mix application service invocation with the existing agent management API namespace and increase compatibility risk.
- Support both positional `args` and named `params` bodies immediately.
  Rejected because the first change should define one stable, testable binding model before expanding the surface.
- Add unauthenticated service invocation for convenience.
  Rejected because application service exposure should not create a weaker path than the existing HTTP API boundary.
- Combine HTTP exposure with runtime packaging.
  Rejected because packaging depends on a stable callable runtime surface and is already a separate M36 planned change.
