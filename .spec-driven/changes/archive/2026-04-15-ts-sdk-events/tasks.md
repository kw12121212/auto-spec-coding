# Tasks: ts-sdk-events

## Implementation

- [x] Add a polling-backed event subscription helper module under `sdk/ts/src/` using the existing `SpecDrivenClient.pollEvents()` method.
- [x] Add or update TypeScript SDK event subscription option and lifecycle types needed for the public package API.
- [x] Export the new event subscription helper surface from `sdk/ts/src/index.ts`.
- [x] Preserve the observable behavior of the existing `pollEvents()` method while integrating the higher-level helper.

## Testing

- [x] Run `npm test` from `sdk/ts/` to execute the TypeScript SDK unit test suite, including the event subscription coverage added by this change.
- [x] Run `npm run lint` from `sdk/ts/`.
- [x] Run `npm run typecheck` from `sdk/ts/`.

## Verification

- [x] Verify the public SDK surface offers a polling-backed subscription helper without changing the existing `/api/v1/events` HTTP contract.
- [x] Verify callers can start from an explicit cursor, continue from `nextCursor`, and stop polling cleanly.
