# release-prep

## What

Prepare the repository for release consumption without performing an actual external publish. This change updates the top-level documentation and quickstarts to reflect the implemented Java SDK, JSON-RPC, and HTTP REST API surfaces, adds example coverage for those three interfaces, and completes the Maven project metadata needed for local release verification.

## Why

M16 is the active wrap-up milestone and `integration-testing` is already complete, but the repository still looks pre-implementation from a release consumer's perspective. [README.md](/home/code/Code/auto-spec-coding/README.md) still says development has not started, there are no guided examples for the three supported interfaces, and [pom.xml](/home/code/Code/auto-spec-coding/pom.xml) is missing release-facing metadata expected from a consumable Maven artifact. Completing this change closes the remaining planned item in M16 and gives downstream SDK milestones a stable, documented baseline.

## Scope

In scope:
- Refresh repository-facing documentation so it describes the currently implemented system rather than the initial roadmap state
- Add quickstart and usage examples for all three supported integration surfaces: Native Java SDK, JSON-RPC, and HTTP REST API
- Document local release verification steps and example execution flow
- Complete Maven project metadata required for repo-local release readiness and package inspection
- Add tests or validation coverage for any new example code or build metadata touched by the change
- Apply the minimum safe runtime/test fix required to make the proposal's required full-suite validation pass when a tool-free execution path currently fails due to eager permission-store initialization

Out of scope:
- Actual publishing to Maven Central or any external registry
- GPG signing, Sonatype account setup, token management, or live release credentials
- New runtime API features for SDK, JSON-RPC, or HTTP beyond what current specs already describe
- Go SDK, TypeScript SDK, or other post-M16 client deliverables

## Unchanged Behavior

- The observable runtime behavior of the Java SDK, JSON-RPC dispatcher/transport, and HTTP API must remain unchanged
- Existing integration tests remain the source of truth for cross-layer behavioral consistency
- This change does not add new agent capabilities; it only makes the released package and repository easier to consume correctly
