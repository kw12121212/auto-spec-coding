# Questions: release-prep

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Should Maven release preparation target full Maven Central readiness or repo-local release metadata and verification only?
  Context: This decision sets the boundary for `pom.xml` changes, verification tasks, and whether live publish/signing concerns are included.
  A: Repo-local release metadata and verification only.

- [x] Q: Should examples cover only the Java SDK or all three interfaces?
  Context: This decision determines the README/example scope and which release-facing surfaces must be documented before M16 can be considered complete.
  A: Cover all three interfaces: Native Java SDK, JSON-RPC, and HTTP REST API.
