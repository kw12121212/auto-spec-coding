---
mapping:
  implementation:
    - src/main/java/org/specdriven/sdk/LealonePlatform.java
    - src/main/java/org/specdriven/sdk/PlatformConfig.java
    - src/main/java/org/specdriven/sdk/SdkBuilder.java
  tests:
    - src/test/java/org/specdriven/sdk/LealonePlatformTest.java
    - src/test/java/org/specdriven/sdk/SdkBuilderTest.java
---

## ADDED Requirements

### Requirement: PlatformConfig unified parameter holder

The system MUST provide a `PlatformConfig` type that holds all Lealone platform-level parameters in a single typed object.

#### Scenario: Default PlatformConfig matches current behavior
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

`SdkBuilder` MUST accept an optional `PlatformConfig` and use it to assemble the platform when provided.

#### Scenario: Explicit PlatformConfig is applied
- GIVEN a `SdkBuilder` with `platformConfig(PlatformConfig)` set to a non-default config
- WHEN `buildPlatform()` is called
- THEN `platform.database().jdbcUrl()` MUST equal the JDBC URL from the supplied `PlatformConfig`

#### Scenario: YAML platform keys override defaults
- GIVEN a YAML config file containing `platform.jdbcUrl` and/or `platform.compileCachePath` keys
- WHEN `SdkBuilder.config(path).buildPlatform()` is called without an explicit `platformConfig(...)` call
- THEN the platform MUST use the values from the YAML config for the corresponding parameters

#### Scenario: Absent YAML platform keys fall back to defaults
- GIVEN a YAML config file that does not contain `platform.*` keys
- WHEN `SdkBuilder.config(path).buildPlatform()` is called
- THEN the platform MUST use `PlatformConfig.defaults()` values for platform-level parameters

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

## MODIFIED Requirements

### Requirement: Platform core preserves underlying capability behavior
Previously: Introducing `LealonePlatform` MUST NOT change the existing observable behavior of the capabilities it groups.
The system MUST continue to preserve all existing capability behavior when capabilities are assembled using a `PlatformConfig` — including when a non-default `PlatformConfig` is supplied. Behavioral preservation applies to any JDBC URL and compile cache path supplied through `PlatformConfig`.
