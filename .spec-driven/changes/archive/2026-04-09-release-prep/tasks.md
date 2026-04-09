# Tasks: release-prep

## Implementation

- [x] Update the root `README.md` so project status, architecture, prerequisites, and build/test instructions reflect the implemented system instead of the initial roadmap state
- [x] Add repository quickstart/example coverage for the Native Java SDK flow
- [x] Add repository usage examples for JSON-RPC and HTTP REST API flows, aligned with current supported operations
- [x] Update `pom.xml` with repo-local release metadata needed for a consumable Maven artifact
- [x] Add or update validation/tests for any committed example code introduced by the change
- [x] Apply the minimum safe fix needed so tool-free orchestrator/skill-executor paths do not fail the required full-suite validation because of eager permission-store initialization

## Testing

- [x] Validation: run `mvn compile -q` to verify the project still compiles after documentation/build-metadata changes
- [x] Unit test: run `mvn test -q -Dsurefire.useFile=false` to confirm the existing test suite still passes

## Verification

- [x] Verify the final README and examples cover all three supported interfaces: Java SDK, JSON-RPC, and HTTP
- [x] Verify the change stays within repo-local release readiness and does not introduce live publish/signing requirements
- [x] Verify the proposal, tasks, and delta spec remain aligned with the M16 `release-prep` roadmap item
