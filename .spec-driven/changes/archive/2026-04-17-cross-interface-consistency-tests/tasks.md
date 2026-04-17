# Tasks: cross-interface-consistency-tests

## Implementation

- [x] Review current cross-layer, HTTP, JSON-RPC, and SDK tests against the
  existing shared observable contracts in `sdk/sdk-public-api.md`,
  `api/http-e2e-tests.md`, and `api/jsonrpc-e2e-tests.md`.
- [x] Extend cross-interface regression tests to cover shared happy-path
  behavior and lifecycle parity where coverage is currently missing.
- [x] Extend cross-interface regression tests to cover shared error semantics
  and supported divergence boundaries where coverage is currently missing.
- [x] Make only the smallest local test-fixture or assertion-helper changes
  needed to support the added consistency checks.

## Testing

- [x] Run validation build `mvn compile -q`.
- [x] Run targeted unit tests `mvn -q -Dtest=CrossLayerConsistencyTest,HttpE2eTest,JsonRpcEndToEndTest,SpecDrivenTest,SdkAgentTest test -Dsurefire.useFile=false`.
- [x] Run full unit test suite `mvn test -q -Dsurefire.useFile=false`.

## Verification

- [x] Verify the added tests map only to already-specified observable behavior
  in `sdk/sdk-public-api.md`, `api/http-e2e-tests.md`, and
  `api/jsonrpc-e2e-tests.md`.
- [x] Verify the change does not broaden scope into M40-style fixture
  standardization, flaky-test hardening, or quality-gate redesign.
