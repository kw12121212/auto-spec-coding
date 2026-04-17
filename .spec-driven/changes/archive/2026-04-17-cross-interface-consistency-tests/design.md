# Design: cross-interface-consistency-tests

## Approach

Add regression coverage directly around the current observable contracts already
defined in the main specs that govern the three public entry surfaces:
- `sdk/sdk-public-api.md`
- `api/http-e2e-tests.md`
- `api/jsonrpc-e2e-tests.md`

The implementation should prefer extending the existing cross-layer test and the
existing focused HTTP, JSON-RPC, and SDK test classes instead of introducing a
new shared test framework layer. Expected touch points are:
- `src/test/java/org/specdriven/agent/integration/CrossLayerConsistencyTest.java`
- `src/test/java/org/specdriven/agent/http/HttpE2eTest.java`
- `src/test/java/org/specdriven/agent/jsonrpc/JsonRpcEndToEndTest.java`
- `src/test/java/org/specdriven/sdk/SpecDrivenTest.java`
- `src/test/java/org/specdriven/sdk/SdkAgentTest.java`

Where a behavior cannot be asserted identically because one surface exposes a
protocol-shaped representation, the tests should assert parity at the level of
the shared logical outcome instead of forcing byte-for-byte equality across
transport formats.

## Key Decisions

- Treat this as a test-only change with no observable spec delta.
  Rationale: the roadmap item is regression protection for already-defined
  behavior, not feature expansion.

- Reuse and extend the current cross-layer test rather than building a new
  repository-wide consistency harness first.
  Rationale: that keeps the change minimal and avoids drifting into M40 fixture
  standardization work.

- Distinguish shared logical behavior from protocol-specific encoding details.
  Rationale: the roadmap warns against misclassifying reasonable interface
  differences as defects, so assertions should focus on parity of supported
  outcomes rather than forcing transport-level sameness where the specs do not
  require it.

- Use repository-standard Maven daemon commands for verification.
  Rationale: M39 explicitly standardizes on `mvnd` for Maven-based verification
  in this milestone.

## Alternatives Considered

- Create a new generalized cross-interface test harness before adding cases.
  Rejected because it broadens the work into reusable test infrastructure, which
  belongs in M40.

- Write delta spec files to restate existing parity expectations.
  Rejected because the proposal does not introduce new observable behavior; a
  prose-only spec delta would misrepresent the change as a functional contract
  expansion.

- Defer this work until after flaky-test or fixture-standardization changes.
  Rejected because the roadmap explicitly sequences `cross-interface-consistency-tests`
  as the remaining M39 item before M40 infrastructure work.
