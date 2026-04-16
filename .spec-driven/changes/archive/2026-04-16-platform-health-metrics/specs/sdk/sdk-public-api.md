---
mapping:
  implementation:
    - src/main/java/org/specdriven/sdk/SpecDriven.java
    - src/main/java/org/specdriven/sdk/SdkBuilder.java
  tests:
    - src/test/java/org/specdriven/sdk/SpecDrivenPlatformTest.java
---

## ADDED Requirements

### Requirement: SpecDriven platform accessor

`SpecDriven` MUST expose a `platform()` method returning the assembled `LealonePlatform`.

#### Scenario: platform() returns non-null platform after build()
- GIVEN a `SpecDriven` instance built via `SpecDriven.builder().build()`
- WHEN `platform()` is called
- THEN it MUST return a non-null `LealonePlatform` instance

#### Scenario: platform() exposes checkHealth after build
- GIVEN a `SpecDriven` instance built via `SpecDriven.builder().build()`
- WHEN `sdk.platform().checkHealth()` is called
- THEN it MUST return a non-null `PlatformHealth` result without throwing
