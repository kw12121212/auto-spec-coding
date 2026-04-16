---
mapping:
  implementation:
    - src/main/java/org/specdriven/sdk/LealonePlatform.java
    - src/main/java/org/specdriven/sdk/SdkBuilder.java
  tests:
    - src/test/java/org/specdriven/sdk/LealonePlatformTest.java
    - src/test/java/org/specdriven/sdk/SdkBuilderTest.java
---

## ADDED Requirements

### Requirement: Typed Sandlock capability access

`LealonePlatform` MUST expose stable typed access to a Sandlock-backed execution
capability for the repository's M38 profile runtime work.

#### Scenario: platform exposes Sandlock capability
- GIVEN a constructed `LealonePlatform`
- WHEN the caller requests its `sandlock()` capability
- THEN the platform MUST return a non-null typed capability handle

#### Scenario: Sandlock capability uses existing platform assembly path
- GIVEN a caller constructs the platform through `LealonePlatform.builder()` or
  `SpecDriven.builder()`
- WHEN the resulting platform exposes `sandlock()`
- THEN that capability MUST use the same assembled project configuration and
  environment-profile selection already associated with the platform
- AND the caller MUST NOT need to assemble a second unrelated runtime
  configuration path to use Sandlock-backed execution
