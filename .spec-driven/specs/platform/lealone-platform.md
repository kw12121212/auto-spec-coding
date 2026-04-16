---
mapping:
  implementation:
    - src/main/java/org/specdriven/sdk/LealonePlatform.java
    - src/main/java/org/specdriven/sdk/PlatformConfig.java
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

The system MUST preserve all existing capability behavior when capabilities are assembled using a `PlatformConfig` — including when a non-default `PlatformConfig` is supplied. Behavioral preservation applies to any JDBC URL and compile cache path supplied through `PlatformConfig`.

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
