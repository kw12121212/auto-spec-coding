# Tasks: ts-sdk-tests

## Implementation

- [x] Update or confirm `sdk/ts/src/integration.test.ts` covers the core TypeScript SDK workflow against a mocked backend
- [x] Ensure the integration suite documents and enforces the hermetic no-live-backend contract for `ts-sdk-tests`
- [x] Add the TypeScript SDK delta spec describing the accepted hermetic integration-test behavior

## Testing

- [x] Run lint: `cd sdk/ts && npm run lint`
- [x] Run type-check: `cd sdk/ts && npm run typecheck`
- [x] Run unit tests: `cd sdk/ts && npm test`

## Verification

- [x] Verify `sdk/ts/src/integration.test.ts` exercises core workflow success, cursor progression, API error propagation, and hermetic execution without external network calls
- [x] Verify the change does not modify the TypeScript SDK public runtime API or require Java backend code changes
