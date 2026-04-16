---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/http/ServiceHttpInvocationHandler.java
    - src/main/java/org/specdriven/agent/http/ServiceRuntimeLauncher.java
    - src/main/java/org/specdriven/cli/SpecDrivenCliMain.java
  tests:
    - src/test/java/org/specdriven/agent/http/ServiceRuntimeLauncherTest.java
    - src/test/java/org/specdriven/cli/SpecDrivenCliMainTest.java
---

## ADDED Requirements

### Requirement: SDK-backed service runtime assembly

The service runtime entrypoint MUST use the existing SDK/platform assembly path when starting a service application.

#### Scenario: runtime uses assembled platform
- GIVEN the service runtime entrypoint starts a supported `services.sql` application
- WHEN it assembles runtime capabilities
- THEN it MUST use the same platform-backed SDK assembly path exposed by `SpecDriven` and `LealonePlatform`
- AND it MUST NOT silently create a second unrelated platform configuration path for service runtime startup

#### Scenario: runtime bootstrap uses existing service bootstrap behavior
- GIVEN a service runtime startup request references a readable `services.sql` file
- WHEN the runtime applies service application bootstrap
- THEN it MUST use the supported `services.sql` bootstrap behavior already exposed by the SDK/platform surface
- AND startup MUST observe the existing success and failure semantics for supported bootstrap input

#### Scenario: existing SDK agent flow remains compatible
- GIVEN application code already uses `SpecDriven.builder().build()` and `createAgent()`
- WHEN service runtime packaging is added
- THEN the existing agent-oriented SDK flow MUST remain available
- AND its observable behavior MUST remain unchanged unless the caller explicitly uses the service runtime entrypoint
