---
mapping:
  implementation:
    - sdk/ts/package.json
  tests:
    - sdk/ts/src/integration.test.ts
---

## ADDED Requirements

### Requirement: TypeScript SDK hermetic integration coverage
The repository MUST provide a hermetic integration test suite for the TypeScript SDK that exercises the core client workflow against a mocked backend without requiring a live Java backend.

#### Scenario: Full client workflow succeeds against mocked backend
- GIVEN the TypeScript SDK integration test suite is running in the `sdk/ts/` workspace
- AND the backend HTTP surface is provided by a mocked test server
- WHEN the suite exercises `health()`, `listTools()`, `runAgent()`, `getAgentState()`, `stopAgent()`, and `pollEvents()`
- THEN the workflow MUST complete successfully without requiring a live Java backend process

#### Scenario: Integration suite verifies cursor progression
- GIVEN the mocked backend returns event polling cursors
- WHEN the integration suite polls events across multiple requests
- THEN the suite MUST verify that later requests continue from the returned cursor

#### Scenario: Integration suite verifies API error propagation
- GIVEN the mocked backend returns a typed API failure such as HTTP 400 or HTTP 404
- WHEN the integration suite invokes the corresponding SDK method
- THEN the suite MUST verify that the SDK surfaces an `ApiError` with the expected HTTP status and error metadata

#### Scenario: Integration suite remains hermetic
- GIVEN the integration suite runs with the mocked backend enabled
- WHEN the tests complete successfully
- THEN they MUST not require external network access or a separately started Java backend service
