# ts-sdk-tests

## What

Complete the remaining TypeScript SDK roadmap item by defining and aligning hermetic integration-test coverage for `sdk/ts/`. The change will treat the existing `vitest` + `msw` workflow as the accepted integration boundary for this milestone: the TypeScript SDK test suite must exercise the core client flow against a mocked backend without requiring a live Java server.

## Why

`M21 - TypeScript Client SDK` is one change away from completion. The repository already contains the TypeScript SDK package, unit tests, and an existing `sdk/ts/src/integration.test.ts`, but there is still an unresolved planning mismatch: the roadmap milestone notes say integration tests require a running Java backend, while the repository's current integration test file explicitly documents a hermetic mock-backed contract.

This proposal resolves that mismatch in favor of the lower-risk, already-grounded path the user approved: finish `ts-sdk-tests` as a hermetic mocked integration-test change, and leave live Java-backend coverage to a later dedicated change if the roadmap still needs it.

## Scope

In scope:
- Define the remaining `ts-sdk-tests` change as hermetic mocked integration coverage for `sdk/ts/`
- Update or confirm `sdk/ts/src/integration.test.ts` coverage for the core workflow: `health()`, `listTools()`, `runAgent()`, `getAgentState()`, `stopAgent()`, and `pollEvents()`
- Verify error propagation and cursor progression behavior through the mocked backend contract
- Record the observable test contract in a delta spec for `.spec-driven/specs/sdk/ts-client-sdk.md`
- Use the existing TypeScript workspace commands for validation: lint, type-check, and test

Out of scope:
- Requiring a live Java backend for this change
- Adding new SDK runtime features or changing the public client API
- Browser-runtime support or cross-process end-to-end transport coverage
- Any Java backend implementation changes

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- The TypeScript SDK public runtime API remains unchanged
- Existing HTTP request semantics, auth headers, retry behavior, and typed error behavior remain unchanged
- This change does not add any new Java backend requirements or modify backend code
