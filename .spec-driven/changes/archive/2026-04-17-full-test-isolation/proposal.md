# full-test-isolation

## What

Stabilize the repository's default Maven test workflow so `mvn test` can complete successfully under the committed build configuration without cross-test interference from shared embedded Lealone state.

Document the behavior as a repository-local verification guarantee rather than a one-off local workaround.

## Why

The current full test suite is not hermetic under the committed surefire configuration. Multiple tests and SDK/platform entrypoints default to the same embedded Lealone database path, and class-level parallel execution makes unrelated tests fail with file-lock and initialization errors.

This blocks confidence in normal repository verification and undermines the release-facing expectation that repo-local verification catches regressions before delivery.

## Scope

In scope:
- The minimum build or test configuration changes required so `mvn test` runs reliably in the repository
- Targeted test or default-config isolation fixes only if configuration-only changes are insufficient
- Verification updates for the repo-local test workflow

Out of scope:
- Broad test refactors unrelated to the locking failures
- Production runtime behavior changes unrelated to test isolation
- Performance optimization of the test suite beyond what is needed for stable execution

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- Observable SDK, JSON-RPC, HTTP, and service-runtime behavior
- Existing focused compatibility verification commands already documented for Lealone alignment
