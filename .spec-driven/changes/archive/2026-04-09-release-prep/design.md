# Design: release-prep

## Approach

Treat release preparation as a repository-surface change, not a runtime feature change.

The work will proceed in three parallel tracks:

1. Documentation refresh
- Update the root README so the project status, architecture description, prerequisites, build/test commands, and supported interfaces match the implemented codebase
- Add concise quickstart material for the Java SDK and usage examples for JSON-RPC and HTTP
- Keep examples aligned with current observable behavior from the existing specs and end-to-end tests

2. Release metadata completion
- Extend `pom.xml` with release-facing Maven metadata that is safe to commit and verify locally
- Limit scope to metadata and local package readiness; do not include live publish credentials or an actual publish workflow

3. Verification
- Use Maven compile/test commands already established in the repository to ensure the release-facing edits do not regress the build
- Validate that all examples and commands referenced in docs correspond to existing supported surfaces
- If the required full-suite validation is blocked by a repository-local regression triggered by the documented flows, apply the narrowest fix needed to restore the documented behavior and keep the release-prep workflow shippable

## Key Decisions

- Scope release preparation to repo-local readiness, not external publishing
  Rationale: this matches the user-confirmed boundary and avoids introducing external account, signing, or infrastructure dependencies into a documentation-focused change.

- Cover all three interfaces in examples
  Rationale: M16 is about project wrap-up, and the repository should present the Java SDK, JSON-RPC, and HTTP API as first-class supported surfaces rather than only documenting one of them.

- Add a dedicated release-facing spec instead of changing runtime specs
  Rationale: the primary behavioral change here is what the repository and artifact expose to consumers, not how the runtime APIs behave. A dedicated spec keeps that boundary explicit.

- Limit any runtime fix to validation unblockers that are directly exposed by the required release-prep verification commands
  Rationale: the change still centers on release readiness, but the workflow cannot finish if the repository's required validation command fails for an already-defined behavior that this change depends on.

## Alternatives Considered

- Only document the Java SDK
  Rejected because it would leave the two other supported integration surfaces under-documented despite M16 explicitly being a release/readiness milestone.

- Require full Maven Central readiness in this change
  Rejected because it would expand scope into signing and external publishing concerns that are not required for local release preparation.

- Fold release requirements into `sdk-public-api.md`, `jsonrpc-handlers.md`, and `http-api.md`
  Rejected because those specs describe runtime behavior. Release docs and artifact metadata are better tracked as their own observable repository-facing contract.
