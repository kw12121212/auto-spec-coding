---
mapping:
  implementation:
    - src/main/java/org/specdriven/sdk/SdkBuilder.java
    - src/main/java/org/specdriven/sdk/SpecDriven.java
    - src/main/java/org/specdriven/sdk/LealonePlatform.java
  tests:
    - src/test/java/org/specdriven/sdk/SdkBuilderTest.java
    - src/test/java/org/specdriven/sdk/SpecDrivenPlatformTest.java
    - src/test/java/org/specdriven/sdk/SpecDrivenTest.java
---

## ADDED Requirements

### Requirement: SDK-compatible service application bootstrap coexistence

The first declarative service application bootstrap path MUST coexist with the existing SDK surface without changing the observable behavior of agent-oriented SDK usage.

#### Scenario: SDK bootstrap delegates to the assembled platform runtime
- GIVEN application code that uses the supported SDK bootstrap entry path with a readable `services.sql` file
- WHEN bootstrap runs through `SpecDriven`
- THEN the bootstrap MUST use the same assembled platform runtime exposed by `sdk.platform()`
- AND it MUST NOT reconstruct a separate embedded JDBC configuration path outside that assembled platform

#### Scenario: existing SDK agent flow remains compatible
- GIVEN application code that already uses `SpecDriven.builder().build()` and `createAgent()`
- WHEN the repository adds the first service application bootstrap capability
- THEN the existing agent-oriented SDK flow MUST remain available
- AND its observable behavior MUST remain unchanged unless the caller explicitly uses the supported bootstrap entry path

#### Scenario: bootstrap does not redefine current public API layers
- GIVEN the repository already exposes SDK, JSON-RPC, and `/api/v1/*` agent API entry paths
- WHEN the first service application bootstrap contract is introduced
- THEN that contract MUST coexist with those existing entry paths
- AND it MUST NOT require those existing entry paths to proxy through application bootstrap in order to preserve current behavior
