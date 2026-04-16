---
mapping:
  implementation:
    - src/main/java/org/specdriven/sdk/LealonePlatform.java
    - src/main/java/org/specdriven/sdk/PlatformHealth.java
    - src/main/java/org/specdriven/sdk/PlatformMetrics.java
    - src/main/java/org/specdriven/sdk/SubsystemHealth.java
    - src/main/java/org/specdriven/sdk/SubsystemStatus.java
  tests:
    - src/test/java/org/specdriven/sdk/PlatformHealthTest.java
    - src/test/java/org/specdriven/sdk/PlatformMetricsTest.java
---

## ADDED Requirements

### Requirement: SubsystemStatus enum

The system MUST provide a `SubsystemStatus` enum in `org.specdriven.sdk` with values `UP`, `DEGRADED`, and `DOWN`.

### Requirement: SubsystemHealth record

The system MUST provide an immutable `SubsystemHealth` record in `org.specdriven.sdk` with fields `name` (String), `status` (SubsystemStatus), and `message` (String, nullable).

### Requirement: PlatformHealth record

The system MUST provide an immutable `PlatformHealth` record in `org.specdriven.sdk` with fields `overallStatus` (SubsystemStatus), `subsystems` (List<SubsystemHealth>), and `probedAt` (long, epoch milliseconds).

#### Scenario: Overall status derives from worst subsystem
- GIVEN a `PlatformHealth` with one `DOWN` subsystem and three `UP` subsystems
- WHEN `overallStatus()` is called
- THEN it MUST return `DOWN`

#### Scenario: Overall status is DEGRADED when no subsystem is DOWN
- GIVEN a `PlatformHealth` with one `DEGRADED` subsystem and three `UP` subsystems
- WHEN `overallStatus()` is called
- THEN it MUST return `DEGRADED`

#### Scenario: Overall status is UP when all subsystems are UP
- GIVEN a `PlatformHealth` with all subsystems reporting `UP`
- WHEN `overallStatus()` is called
- THEN it MUST return `UP`

### Requirement: PlatformMetrics record

The system MUST provide an immutable `PlatformMetrics` record in `org.specdriven.sdk` with fields `promptTokens` (long), `completionTokens` (long), `compilationOps` (long), `llmCacheHits` (long), `llmCacheMisses` (long), `toolCacheHits` (long), `toolCacheMisses` (long), `interactionCount` (long), and `snapshotAt` (long, epoch milliseconds).

### Requirement: LealonePlatform health check

`LealonePlatform` MUST provide a `checkHealth()` method that probes each capability domain and returns a `PlatformHealth`.

#### Scenario: Probe succeeds for all subsystems
- GIVEN a `LealonePlatform` with all capability domains accessible
- WHEN `checkHealth()` is called
- THEN it MUST return a `PlatformHealth` with four `SubsystemHealth` entries (DB, LLM, Compiler, Agent)
- AND `overallStatus()` MUST reflect the aggregated result
- AND `probedAt()` MUST be within a reasonable window of the current time

#### Scenario: DB probe failure marks DB as DOWN
- GIVEN a `LealonePlatform` configured with an unreachable JDBC URL
- WHEN `checkHealth()` is called
- THEN the DB `SubsystemHealth` MUST have status `DOWN`
- AND the `message` field MUST contain a non-null description

#### Scenario: LLM probe returns DEGRADED when no provider is registered
- GIVEN a `LealonePlatform` assembled with an empty LLM provider registry
- WHEN `checkHealth()` is called
- THEN the LLM `SubsystemHealth` MUST have status `DEGRADED`

#### Scenario: checkHealth publishes PLATFORM_HEALTH_CHECKED event
- GIVEN a `LealonePlatform` with an EventBus accessible
- WHEN `checkHealth()` is called
- THEN a `PLATFORM_HEALTH_CHECKED` event MUST be published to the EventBus
- AND the event metadata MUST include `overallStatus` and `probeDurationMs`

### Requirement: LealonePlatform metrics

`LealonePlatform` MUST provide a `metrics()` method that returns a `PlatformMetrics` snapshot of accumulated counters since the most recent `start()` call.

#### Scenario: Initial metrics are zero
- GIVEN a `LealonePlatform` that has been started but has received no metric-bearing events
- WHEN `metrics()` is called
- THEN all counter fields MUST be zero
- AND `snapshotAt()` MUST be within a reasonable window of the current time

#### Scenario: Compilation ops counter increments on SKILL_HOT_LOAD_OPERATION events
- GIVEN a `LealonePlatform` that has been started
- WHEN a `SKILL_HOT_LOAD_OPERATION` event is published to the EventBus
- THEN `metrics().compilationOps()` MUST return a value greater than its previous value

#### Scenario: Interaction count increments on INTERACTIVE_COMMAND_HANDLED events
- GIVEN a `LealonePlatform` that has been started
- WHEN an `INTERACTIVE_COMMAND_HANDLED` event is published to the EventBus
- THEN `metrics().interactionCount()` MUST return a value greater than its previous value

#### Scenario: metrics publishes PLATFORM_METRICS_SNAPSHOT event
- GIVEN a `LealonePlatform` with an EventBus accessible
- WHEN `metrics()` is called
- THEN a `PLATFORM_METRICS_SNAPSHOT` event MUST be published
- AND the event metadata MUST include at least `compilationOps` and `interactionCount`

### Requirement: Metric counter lifecycle

`LealonePlatform` MUST register EventBus subscriptions for metric accumulation during `start()` and remove them during `stop()`.

#### Scenario: Counters do not accumulate after stop
- GIVEN a `LealonePlatform` that has been started then stopped
- WHEN metric-bearing EventBus events are published after `stop()`
- THEN the counters MUST NOT change from their values at stop time
