---
mapping:
  implementation:
    - src/main/java/org/specdriven/sdk/LealonePlatform.java
    - src/main/java/org/specdriven/sdk/PlatformHealth.java
    - src/main/java/org/specdriven/sdk/SubsystemHealth.java
    - src/main/java/org/specdriven/agent/event/EventType.java
  tests:
    - src/test/java/org/specdriven/sdk/LealonePlatformTest.java
    - src/test/java/org/specdriven/sdk/PlatformHealthTest.java
    - src/test/java/org/specdriven/sdk/PlatformMetricsTest.java
---

## MODIFIED Requirements

### Requirement: LealonePlatform health check
Previously: `LealonePlatform` MUST provide a `checkHealth()` method that probes each capability domain and returns a `PlatformHealth`.
`LealonePlatform` MUST provide a `checkHealth()` method that probes each capability domain, including Sandlock-backed profile execution readiness, and returns a `PlatformHealth`.

#### Scenario: probe succeeds for all subsystems
- GIVEN a `LealonePlatform` with all capability domains accessible
- WHEN `checkHealth()` is called
- THEN it MUST return a `PlatformHealth` with five `SubsystemHealth` entries (`db`, `llm`, `compiler`, `agent`, and `sandlock`)
- AND `overallStatus()` MUST reflect the aggregated result
- AND `probedAt()` MUST be within a reasonable window of the current time

#### Scenario: unsupported Sandlock host is visible in platform health
- GIVEN a `LealonePlatform` whose Sandlock capability cannot run on the current host
- WHEN `checkHealth()` is called
- THEN the returned health result MUST include subsystem `sandlock`
- AND the `sandlock` subsystem MUST have status `DEGRADED`
- AND its message MUST identify why Sandlock-backed execution is unavailable

#### Scenario: missing or invalid selected profile is visible in platform health
- GIVEN a `LealonePlatform` whose selected or default environment profile is missing or invalid for isolated execution
- WHEN `checkHealth()` is called
- THEN the returned health result MUST include subsystem `sandlock`
- AND the `sandlock` subsystem MUST have status `DEGRADED`
- AND its message MUST identify the invalid profile condition
