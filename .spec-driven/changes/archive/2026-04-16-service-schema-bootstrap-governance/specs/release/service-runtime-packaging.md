---
mapping:
  implementation:
    - README.md
    - src/main/java/org/specdriven/agent/http/ServiceRuntimeLauncher.java
    - src/main/java/org/specdriven/cli/SpecDrivenCliMain.java
  tests:
    - src/test/java/org/specdriven/agent/http/ServiceRuntimeLauncherTest.java
    - src/test/java/org/specdriven/cli/SpecDrivenCliMainTest.java
---

## ADDED Requirements

### Requirement: Runtime configuration governance boundary
The Java service runtime entrypoint MUST assemble startup runtime settings only from its supported explicit inputs and platform defaults, and MUST NOT allow `services.sql` bootstrap input to override or inject runtime configuration.

#### Scenario: explicit runtime settings remain source of truth
- GIVEN the operator starts a supported service application through the Java runtime entrypoint
- WHEN startup succeeds
- THEN the effective HTTP bind settings in startup output MUST come from the supported runtime entrypoint inputs and defaults
- AND they MUST NOT be inferred from `services.sql` contents

#### Scenario: bootstrap input cannot redefine runtime settings
- GIVEN a readable `services.sql` file attempts to declare runtime host, port, JDBC, compile-cache, or authentication settings
- WHEN the operator starts the service application through the supported Java runtime entrypoint
- THEN startup MUST fail explicitly
- AND it MUST return a bootstrap-related failure instead of silently overriding runtime settings

## MODIFIED Requirements

### Requirement: Minimal runtime configuration
Previously: The Java service runtime entrypoint MUST accept the minimal runtime settings required to start the service application and application-service HTTP endpoint.
The Java service runtime entrypoint MUST accept only the minimal governed runtime settings required to start the service application and application-service HTTP endpoint, and it MUST validate those settings before bootstrap-managed statements or HTTP startup begin.

#### Scenario: runtime accepts governed startup settings
- GIVEN a supported `services.sql` file
- AND the caller supplies supported HTTP bind settings and supported platform runtime overrides
- WHEN startup succeeds
- THEN the structured startup output MUST include the effective HTTP bind settings
- AND callers MUST be able to use those settings to reach the service HTTP namespace

#### Scenario: invalid runtime configuration fails before bootstrap execution
- GIVEN the caller supplies invalid required runtime configuration
- WHEN the operator starts the service application through the supported Java runtime entrypoint
- THEN startup MUST fail explicitly before bootstrap-managed statements execute
- AND the failure output MUST identify the invalid configuration
