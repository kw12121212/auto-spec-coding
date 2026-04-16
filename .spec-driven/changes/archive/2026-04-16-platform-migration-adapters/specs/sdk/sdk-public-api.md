---
mapping:
  implementation:
    - src/main/java/org/specdriven/sdk/SdkBuilder.java
    - src/main/java/org/specdriven/sdk/SpecDriven.java
    - src/main/java/org/specdriven/sdk/LealonePlatform.java
  tests:
    - src/test/java/org/specdriven/sdk/SdkBuilderTest.java
    - src/test/java/org/specdriven/sdk/SpecDrivenTest.java
    - src/test/java/org/specdriven/sdk/SpecDrivenPlatformTest.java
---

## MODIFIED Requirements

### Requirement: SpecDriven platform accessor
Previously: `SpecDriven` MUST expose a `platform()` method returning the assembled `LealonePlatform`.
`SpecDriven` MUST expose a `platform()` method returning the assembled `LealonePlatform`. SDK-owned Lealone-backed helper services assembled after `build()` MUST use the same effective platform configuration represented by that assembled platform.

#### Scenario: Lazy SDK service uses assembled platform configuration
- GIVEN a `SpecDriven` instance built with a custom `PlatformConfig`
- WHEN the caller triggers initialization of an SDK-owned Lealone-backed helper service after `build()`
- THEN that service MUST use the same effective platform configuration exposed by `sdk.platform()`
- AND it MUST NOT silently fall back to the repository default embedded JDBC URL

#### Scenario: Existing platform accessor remains compatible
- GIVEN existing application code that calls `sdk.platform()` after `SpecDriven.builder().build()`
- WHEN the migration adapters are present
- THEN `platform()` MUST remain available and non-null
- AND existing platform-backed behavior observed through that accessor MUST remain compatible with prior behavior
