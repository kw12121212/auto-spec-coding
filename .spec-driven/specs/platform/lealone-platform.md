---
mapping:
  implementation:
    - src/main/java/org/specdriven/sdk/LealonePlatform.java
    - src/main/java/org/specdriven/sdk/PlatformConfig.java
    - src/main/java/org/specdriven/sdk/PlatformHealth.java
    - src/main/java/org/specdriven/sdk/PlatformMetrics.java
    - src/main/java/org/specdriven/sdk/SubsystemHealth.java
    - src/main/java/org/specdriven/sdk/SubsystemStatus.java
    - src/main/java/org/specdriven/sdk/SdkBuilder.java
    - src/main/java/org/specdriven/sdk/SpecDriven.java
    - src/main/java/org/specdriven/agent/loop/InteractiveSessionFactory.java
    - src/main/java/org/specdriven/agent/agent/LlmProviderRegistry.java
    - src/main/java/org/specdriven/agent/llm/RuntimeLlmConfigStore.java
    - src/main/java/org/specdriven/skill/compiler/SkillSourceCompiler.java
    - src/main/java/org/specdriven/skill/compiler/ClassCacheManager.java
    - src/main/java/org/specdriven/skill/hotload/SkillHotLoader.java
  tests:
    - src/test/java/org/specdriven/sdk/LealonePlatformTest.java
    - src/test/java/org/specdriven/sdk/PlatformHealthTest.java
    - src/test/java/org/specdriven/sdk/PlatformMetricsTest.java
    - src/test/java/org/specdriven/sdk/SdkBuilderTest.java
    - src/test/java/org/specdriven/sdk/SpecDrivenTest.java
---

# Lealone Platform

## ADDED Requirements

### Requirement: LealonePlatform public entry point

The system MUST provide a public `LealonePlatform` entry point that exposes the repository's assembled Lealone-centered platform capabilities without requiring callers to reconstruct those dependencies manually.

#### Scenario: Create platform instance from supported entry path
- GIVEN application code that wants direct platform access
- WHEN it uses the supported public creation path for `LealonePlatform`
- THEN it MUST obtain a non-null platform instance
- AND the instance MUST expose the platform capabilities defined by this change

### Requirement: Typed database capability access

`LealonePlatform` MUST expose a stable typed access path for the database capability used by the repository's Lealone-backed components.

#### Scenario: Platform exposes database capability
- GIVEN a constructed `LealonePlatform`
- WHEN the caller requests its database capability
- THEN the platform MUST return a non-null typed database capability handle
- AND the handle MUST expose the configured Lealone JDBC URL

### Requirement: Typed runtime LLM capability access

`LealonePlatform` MUST expose stable typed access to runtime LLM capability required for provider resolution and persisted runtime configuration integration.

#### Scenario: Platform exposes runtime LLM capability
- GIVEN a constructed `LealonePlatform`
- WHEN the caller requests its runtime LLM capability
- THEN the platform MUST return access to the configured LLM provider registry
- AND it MUST return access to runtime LLM config persistence capability when that capability is part of the assembled platform

#### Scenario: Platform construction tolerates missing runtime config persistence
- GIVEN the runtime LLM config persistence component cannot be initialized in the current environment
- WHEN a caller constructs `LealonePlatform`
- THEN platform construction MUST still succeed
- AND the runtime LLM capability MUST still expose a provider registry
- AND runtime LLM config persistence capability MAY be absent from the assembled platform

### Requirement: Typed compiler and hot-load capability access

`LealonePlatform` MUST expose stable typed access to the skill compilation and activation capability set already used by the repository.

#### Scenario: Platform exposes compiler capability set
- GIVEN a constructed `LealonePlatform`
- WHEN the caller requests its compiler capability set
- THEN the platform MUST expose the configured `SkillSourceCompiler`
- AND it MUST expose the configured `ClassCacheManager`
- AND it MUST expose the configured `SkillHotLoader`

### Requirement: Typed interactive-session capability access

`LealonePlatform` MUST expose stable typed access to interactive-session creation capability.

#### Scenario: Platform exposes interactive session factory
- GIVEN a constructed `LealonePlatform`
- WHEN the caller requests its interactive capability
- THEN the platform MUST return a non-null `InteractiveSessionFactory`

### Requirement: Platform capability access is explicit, not generic

The initial `LealonePlatform` contract MUST expose the capability domains in this change through explicit typed accessors rather than a generic string-keyed or class-keyed registry lookup API.

#### Scenario: Initial platform API avoids generic registry lookup
- GIVEN the initial `LealonePlatform` contract defined by this change
- WHEN a caller inspects the supported capability access pattern
- THEN the observable capability access surface MUST be explicit and typed for the supported domains
- AND the change MUST NOT require a generic registry lookup API to obtain those capabilities

### Requirement: Platform core preserves underlying capability behavior

The system MUST preserve all existing capability behavior when capabilities are assembled using a `PlatformConfig` — including when a non-default `PlatformConfig` is supplied. Behavioral preservation applies to any JDBC URL and compile cache path supplied through `PlatformConfig`, and to any public or SDK-owned Lealone-backed assembly path migrated onto the platform adapter surface.

#### Scenario: Migrated platform-backed entry paths preserve default behavior
- GIVEN existing callers that rely on the repository's default platform configuration
- WHEN they construct the platform or SDK through the supported public entry paths
- THEN the observable behavior of those entry paths MUST remain compatible with the pre-migration behavior
- AND the change MUST NOT require callers to supply new configuration only to preserve existing defaults

#### Scenario: Platform access does not alter LLM runtime semantics
- GIVEN existing runtime LLM behavior for snapshot resolution, mutation, and event publication
- WHEN those capabilities are obtained through `LealonePlatform`
- THEN their observable behavior MUST remain unchanged

#### Scenario: Platform access does not alter interactive-session semantics
- GIVEN existing interactive session lifecycle semantics
- WHEN the interactive capability is obtained through `LealonePlatform`
- THEN session creation and lifecycle behavior MUST remain unchanged

#### Scenario: Platform access does not alter skill hot-load semantics
- GIVEN existing compilation, cache, permission, and trust behavior for skill hot-loading
- WHEN those capabilities are obtained through `LealonePlatform`
- THEN their observable behavior MUST remain unchanged

### Requirement: PlatformConfig unified parameter holder

The system MUST provide a `PlatformConfig` type that holds all Lealone platform-level parameters in a single typed object.

#### Scenario: Default PlatformConfig matches prior behavior
- GIVEN no explicit platform configuration is provided
- WHEN `PlatformConfig.defaults()` is used
- THEN `jdbcUrl()` MUST return the same JDBC URL that the system used before this change
- AND `compileCachePath()` MUST return the same path the system used before this change

#### Scenario: Custom PlatformConfig overrides defaults
- GIVEN a `PlatformConfig` constructed with a custom JDBC URL and a custom compile cache path
- WHEN this config is supplied to the platform builder
- THEN the assembled platform MUST use the custom JDBC URL for its database capability
- AND the assembled compiler capability MUST use the custom compile cache path

### Requirement: SdkBuilder accepts PlatformConfig

`SdkBuilder` MUST accept an optional `PlatformConfig` and use it to assemble the platform when provided. The same effective platform configuration MUST also govern adapted SDK-owned Lealone-backed assembly paths that are created from the resulting SDK instance.

#### Scenario: Explicit PlatformConfig is applied
- GIVEN a `SdkBuilder` with `platformConfig(PlatformConfig)` set to a non-default config
- WHEN `buildPlatform()` is called
- THEN `platform.database().jdbcUrl()` MUST equal the JDBC URL from the supplied `PlatformConfig`

#### Scenario: Explicit PlatformConfig governs adapted SDK assembly
- GIVEN a `SdkBuilder` with `platformConfig(PlatformConfig)` set to a non-default config
- WHEN `build()` creates an SDK instance and that SDK later initializes an adapted Lealone-backed helper service
- THEN the helper service MUST use the same effective platform configuration exposed by `sdk.platform()`
- AND it MUST NOT silently recreate the repository default platform settings

#### Scenario: YAML platform keys override defaults
- GIVEN a YAML config file containing `platform.jdbcUrl` and/or `platform.compileCachePath` keys
- WHEN `SdkBuilder.config(path).buildPlatform()` is called without an explicit `platformConfig(...)` call
- THEN the platform MUST use the values from the YAML config for the corresponding parameters

#### Scenario: Absent YAML platform keys fall back to defaults
- GIVEN a YAML config file that does not contain `platform.*` keys
- WHEN `SdkBuilder.config(path).buildPlatform()` is called
- THEN the platform MUST use `PlatformConfig.defaults()` values for platform-level parameters

#### Scenario: Default PlatformConfig still governs adapted SDK assembly
- GIVEN a `SdkBuilder` without an explicit `platformConfig(...)` call
- WHEN `build()` creates an SDK instance and that SDK later initializes an adapted Lealone-backed helper service
- THEN the helper service MUST use `PlatformConfig.defaults()` or the YAML-derived effective platform configuration, consistent with the assembled platform

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

### Requirement: LealonePlatform lifecycle — start

`LealonePlatform` MUST provide a `start()` method that records the platform as running.

#### Scenario: start() completes without error
- GIVEN a freshly built `LealonePlatform`
- WHEN `start()` is called
- THEN the call MUST complete without throwing an exception

#### Scenario: start() is idempotent
- GIVEN a `LealonePlatform` on which `start()` has already been called
- WHEN `start()` is called a second time
- THEN the call MUST complete without throwing an exception

### Requirement: LealonePlatform lifecycle — stop

`LealonePlatform` MUST provide a `stop()` method that tears down all capability domains in an ordered, exception-tolerant manner.

#### Scenario: stop() completes without error
- GIVEN a `LealonePlatform` that has been started
- WHEN `stop()` is called
- THEN all capability domains MUST be shut down
- AND the call MUST complete without throwing an exception even if an individual subsystem teardown fails

#### Scenario: stop() is idempotent
- GIVEN a `LealonePlatform` on which `stop()` has already been called
- WHEN `stop()` is called a second time
- THEN the call MUST complete without throwing an exception

#### Scenario: close() delegates to stop()
- GIVEN a `LealonePlatform`
- WHEN `close()` is called
- THEN it MUST perform the same teardown as `stop()`
- AND calling `stop()` after `close()` MUST be safe (idempotent)

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
- GIVEN a `LealonePlatform` configured with an unreachable JDBC URL (unregistered driver)
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
