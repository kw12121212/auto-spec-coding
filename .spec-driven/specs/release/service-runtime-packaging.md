---
mapping:
  implementation:
    - README.md
    - src/main/java/org/specdriven/agent/http/ServiceHttpInvocationHandler.java
    - src/main/java/org/specdriven/agent/http/ServiceRuntimeLauncher.java
    - src/main/java/org/specdriven/cli/SpecDrivenCliMain.java
  tests:
    - src/test/java/org/specdriven/agent/http/ServiceRuntimeLauncherTest.java
    - src/test/java/org/specdriven/cli/SpecDrivenCliMainTest.java
---

# Service Runtime Packaging

## Requirements

### Requirement: Java service runtime entrypoint

The system MUST provide a Java runtime entrypoint for starting a supported Lealone service application from a `services.sql` file without requiring the operator to manually assemble platform bootstrap and HTTP exposure steps.

#### Scenario: runtime starts from services.sql
- GIVEN a readable supported `services.sql` file
- AND valid minimal runtime configuration is supplied
- WHEN the operator starts the service application through the supported Java runtime entrypoint
- THEN the runtime MUST attempt to bootstrap that `services.sql` application
- AND it MUST attempt to expose supported application service methods through the existing service HTTP namespace
- AND it MUST return structured startup output indicating startup success

#### Scenario: missing services.sql fails explicitly
- GIVEN the requested `services.sql` path does not reference a readable file
- WHEN the operator starts the service application through the supported Java runtime entrypoint
- THEN startup MUST fail explicitly
- AND the failure output MUST identify the missing or unreadable startup input

#### Scenario: unsupported services.sql input fails explicitly
- GIVEN the requested `services.sql` file contains input outside the supported bootstrap contract
- WHEN the operator starts the service application through the supported Java runtime entrypoint
- THEN startup MUST fail explicitly
- AND the failure output MUST identify unsupported bootstrap input
- AND startup MUST NOT report the application as running

### Requirement: Minimal runtime configuration

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

### Requirement: Runtime startup documentation

The repository MUST document the supported development and packaged-runtime startup commands for service applications.

#### Scenario: developer finds repo-local startup command
- GIVEN a developer wants to run a supported service application from the repository checkout
- WHEN they read the repository runtime documentation
- THEN they MUST find the supported repo-local Java startup command
- AND the command MUST show how to provide a `services.sql` path and minimal runtime settings

#### Scenario: operator finds packaged startup command
- GIVEN an operator has a built runtime artifact
- WHEN they read the repository runtime documentation
- THEN they MUST find the supported packaged-runtime Java startup command
- AND the command MUST identify the required startup input

### Requirement: Runtime startup compatibility

Introducing the service runtime entrypoint MUST preserve existing public integration surfaces.

#### Scenario: agent API remains compatible
- GIVEN the service runtime entrypoint is available
- WHEN callers continue using existing `/api/v1/*` agent management API routes
- THEN those routes MUST keep their existing observable behavior

#### Scenario: SDK and JSON-RPC remain compatible
- GIVEN the service runtime entrypoint is available
- WHEN callers continue using the existing `SpecDriven` SDK or JSON-RPC entrypoints
- THEN those entrypoints MUST keep their existing observable behavior
