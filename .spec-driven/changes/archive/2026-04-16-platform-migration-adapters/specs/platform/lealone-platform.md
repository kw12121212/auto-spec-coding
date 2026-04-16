---
mapping:
  implementation:
    - src/main/java/org/specdriven/sdk/LealonePlatform.java
    - src/main/java/org/specdriven/sdk/PlatformConfig.java
    - src/main/java/org/specdriven/sdk/SdkBuilder.java
    - src/main/java/org/specdriven/sdk/SpecDriven.java
  tests:
    - src/test/java/org/specdriven/sdk/LealonePlatformTest.java
    - src/test/java/org/specdriven/sdk/SdkBuilderTest.java
    - src/test/java/org/specdriven/sdk/SpecDrivenPlatformTest.java
    - src/test/java/org/specdriven/sdk/SpecDrivenTest.java
---

## MODIFIED Requirements

### Requirement: Platform core preserves underlying capability behavior
Previously: The system MUST preserve all existing capability behavior when capabilities are assembled using a `PlatformConfig` — including when a non-default `PlatformConfig` is supplied. Behavioral preservation applies to any JDBC URL and compile cache path supplied through `PlatformConfig`.
The system MUST preserve all existing capability behavior when capabilities are assembled using a `PlatformConfig` — including when a non-default `PlatformConfig` is supplied. Behavioral preservation applies to any JDBC URL and compile cache path supplied through `PlatformConfig`, and to any public or SDK-owned Lealone-backed assembly path migrated onto the platform adapter surface by this change.

#### Scenario: Migrated platform-backed entry paths preserve default behavior
- GIVEN existing callers that rely on the repository's default platform configuration
- WHEN they construct the platform or SDK through the supported public entry paths after this change
- THEN the observable behavior of those entry paths MUST remain compatible with the pre-migration behavior
- AND the change MUST NOT require callers to supply new configuration only to preserve existing defaults

### Requirement: SdkBuilder accepts PlatformConfig
Previously: `SdkBuilder` MUST accept an optional `PlatformConfig` and use it to assemble the platform when provided.
`SdkBuilder` MUST accept an optional `PlatformConfig` and use it to assemble the platform when provided. The same effective platform configuration MUST also govern adapted SDK-owned Lealone-backed assembly paths that are created from the resulting SDK instance.

#### Scenario: Explicit PlatformConfig governs adapted SDK assembly
- GIVEN a `SdkBuilder` with `platformConfig(PlatformConfig)` set to a non-default config
- WHEN `build()` creates an SDK instance and that SDK later initializes an adapted Lealone-backed helper service
- THEN the helper service MUST use the same effective platform configuration exposed by `sdk.platform()`
- AND it MUST NOT silently recreate the repository default platform settings

#### Scenario: Default PlatformConfig still governs adapted SDK assembly
- GIVEN a `SdkBuilder` without an explicit `platformConfig(...)` call
- WHEN `build()` creates an SDK instance and that SDK later initializes an adapted Lealone-backed helper service
- THEN the helper service MUST use `PlatformConfig.defaults()` or the YAML-derived effective platform configuration, consistent with the assembled platform
