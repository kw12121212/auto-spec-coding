# Design: ts-sdk-tests

## Approach

Treat `ts-sdk-tests` as a test-focused completion change, not a runtime-feature change. The implementation should build on the existing TypeScript package structure in `sdk/ts/` and the existing `sdk/ts/src/integration.test.ts` file, using `vitest` and `msw` to simulate the backend HTTP surface.

The integration suite should verify the end-to-end SDK workflow from the TypeScript caller's perspective while staying hermetic: no live Java backend process, no external network dependency, and no change to existing client behavior beyond what is necessary to keep the test contract clear and reliable.

## Key Decisions

- Hermetic mocked integration tests are the accepted scope for this roadmap item. The user explicitly confirmed this, and the repository already contains a matching test harness.
- Reuse the existing `vitest` + `msw` stack rather than introducing a second integration-test framework. This keeps the change minimal and aligned with the current TypeScript workspace.
- Keep the spec delta focused on the integration-test contract, not on new SDK runtime behavior. The runtime API has already been covered by prior TypeScript SDK changes.
- Treat live Java-backend integration as a separate future concern if the roadmap still needs it later. This prevents the current change from silently expanding scope.

## Alternatives Considered

- Require a live Java backend for `ts-sdk-tests` now. Rejected because the user chose the hermetic path and the current repository evidence already supports it.
- Fold live backend coverage into this proposal alongside hermetic tests. Rejected because it would expand the change from one remaining milestone item into two distinct testing strategies.
- Skip a spec delta and treat this as implementation-only cleanup. Rejected because the accepted scope decision changes the milestone's observable testing contract and should be captured explicitly.
