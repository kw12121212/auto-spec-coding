---
mapping:
  implementation:
    - src/main/java/org/specdriven/sdk/SpecDriven.java
    - src/main/java/org/specdriven/sdk/SdkBuilder.java
    - src/main/java/org/specdriven/sdk/LealonePlatform.java
  tests:
    - src/test/java/org/specdriven/sdk/SdkBuilderTest.java
    - src/test/java/org/specdriven/sdk/SpecDrivenTest.java
    - src/test/java/org/specdriven/sdk/LealonePlatformTest.java
---

## MODIFIED Requirements

### Requirement: SpecDriven entry point
Previously: The system MUST provide a `SpecDriven` class in `org.specdriven.sdk` as the sole public entry point for the SDK.
The system MUST provide `SpecDriven` as the primary public agent facade in `org.specdriven.sdk`.

#### Scenario: Existing builder entry remains available
- GIVEN no prior SDK instance
- WHEN `SpecDriven.builder()` is called followed by `.build()`
- THEN it MUST still return a new `SpecDriven` instance with default configuration

## ADDED Requirements

### Requirement: Public LealonePlatform entry point

The SDK public surface MUST additionally expose `LealonePlatform` as a public platform-level entry point for callers that need direct access to assembled Lealone-centered capabilities beyond the agent facade.

#### Scenario: Public platform entry coexists with SpecDriven
- GIVEN application code that needs direct platform capability access
- WHEN it uses the supported public `LealonePlatform` entry path
- THEN it MUST obtain a platform instance without removing or renaming the existing `SpecDriven` entry path

### Requirement: Platform and agent facade compatibility

The introduction of `LealonePlatform` MUST NOT break existing `SpecDriven`-based SDK usage.

#### Scenario: Existing SpecDriven usage remains compatible
- GIVEN existing application code that uses `SpecDriven.builder()` and `createAgent()`
- WHEN the SDK adds `LealonePlatform`
- THEN the existing `SpecDriven` usage path MUST remain supported
- AND its observable behavior MUST remain unchanged unless explicitly modified by a separate change
