---
mapping:
  implementation:
    - src/main/java/org/specdriven/sdk/LealonePlatform.java
    - src/main/java/org/specdriven/sdk/SdkBuilder.java
    - src/main/java/org/specdriven/sdk/SpecDriven.java
  tests:
    - src/test/java/org/specdriven/sdk/LealonePlatformTest.java
    - src/test/java/org/specdriven/sdk/SdkBuilderTest.java
    - src/test/java/org/specdriven/sdk/SpecDrivenPlatformTest.java
---

## ADDED Requirements

### Requirement: Platform-backed service application bootstrap entry

The Lealone platform integration surface MUST support bootstrapping a supported declarative service application from `services.sql` without requiring callers to reconstruct a second parallel runtime configuration path.

#### Scenario: bootstrap uses assembled platform configuration
- GIVEN a caller bootstraps a supported declarative service application through the supported platform entry path
- AND the input is a readable file named `services.sql`
- WHEN the bootstrap reads and applies that file
- THEN the bootstrap MUST execute against the same effective JDBC runtime represented by the assembled `LealonePlatform`
- AND it MUST NOT silently create a separate default JDBC or compile-cache configuration path for the application runtime

#### Scenario: bootstrap accepts the first supported statement set
- GIVEN a readable `services.sql` file for the first `service-app-bootstrap` change
- WHEN the platform-backed bootstrap validates supported startup input
- THEN the file MUST support idempotent `CREATE TABLE IF NOT EXISTS` statements for schema bootstrap
- AND it MUST support idempotent `CREATE SERVICE IF NOT EXISTS` statements for service bootstrap
- AND it MUST treat other statement types or runtime directives as unsupported input for this change

#### Scenario: repeated bootstrap converges idempotently
- GIVEN the same supported `services.sql` input is bootstrapped twice against the same target runtime state
- WHEN the second bootstrap runs
- THEN it MUST converge without requiring manual cleanup of objects created by the first bootstrap
- AND it MUST NOT report success by creating duplicate bootstrap-managed objects

#### Scenario: unsupported bootstrap input fails explicitly
- GIVEN a bootstrap attempt includes declarative application input outside the supported first-change contract
- WHEN bootstrap validation or startup runs
- THEN the platform-backed bootstrap MUST fail explicitly
- AND it MUST NOT silently apply unsupported statements or runtime directives
- AND it MUST reject unsupported input before executing later bootstrap-managed statements from the same file
