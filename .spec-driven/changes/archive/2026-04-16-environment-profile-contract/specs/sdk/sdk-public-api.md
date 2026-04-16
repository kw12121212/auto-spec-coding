---
mapping:
  implementation:
    - src/main/java/org/specdriven/sdk/SdkBuilder.java
    - src/main/java/org/specdriven/sdk/SpecDriven.java
  tests:
    - src/test/java/org/specdriven/sdk/SdkBuilderTest.java
---

## MODIFIED Requirements

### Requirement: SdkBuilder configuration
Previously:
- The system MUST provide a `SdkBuilder` in `org.specdriven.sdk` for configuring SDK instances.

The system MUST provide a `SdkBuilder` in `org.specdriven.sdk` for configuring SDK instances, including environment-profile selection from supported project YAML when that configuration contract is present.

#### Scenario: environment profile default is resolved from project config
- GIVEN a builder with `.config(Path)` referencing a project YAML file that declares supported environment profiles
- AND no explicit environment profile is requested
- WHEN `.build()` is invoked
- THEN the SDK assembly MUST resolve the declared default profile as the effective profile

#### Scenario: explicit environment profile overrides project default
- GIVEN a builder with `.config(Path)` referencing a project YAML file that declares supported environment profiles
- AND a supported caller explicitly requests a declared environment profile name
- WHEN `.build()` is invoked
- THEN SDK assembly MUST use the explicitly requested environment profile instead of the project default

#### Scenario: manual provider registry does not bypass profile config resolution
- GIVEN a builder with both `.config(Path)` and `.providerRegistry(registry)` set
- AND the referenced project YAML file declares supported environment profiles
- WHEN `.build()` is invoked
- THEN the manually provided registry MUST still take precedence over auto-assembled providers
- AND the project YAML environment profile contract MUST still be loaded and validated

#### Scenario: unknown explicit environment profile fails build
- GIVEN a builder with `.config(Path)` referencing a project YAML file that declares supported environment profiles
- WHEN a supported caller explicitly requests an undeclared environment profile name and `.build()` is invoked
- THEN the build MUST fail explicitly with a configuration error

### Requirement: SpecDriven entry point
Previously:
- The system MUST provide `SpecDriven` as the primary public agent facade in `org.specdriven.sdk`.

The system MUST provide `SpecDriven` as the primary public agent facade in `org.specdriven.sdk`, and the assembled SDK instance MUST expose its effective flattened configuration for observable inspection.

#### Scenario: assembled SDK exposes effective selected environment profile
- GIVEN a `SpecDriven` instance built from project YAML that declares supported environment profiles
- WHEN the SDK resolves an effective environment profile during assembly
- THEN supported callers MUST be able to inspect the assembled flattened configuration
- AND that configuration MUST identify the selected environment profile name
