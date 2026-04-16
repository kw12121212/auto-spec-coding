---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/config/Config.java
    - src/main/java/org/specdriven/agent/config/ConfigLoader.java
    - src/main/java/org/specdriven/sdk/SdkBuilder.java
  tests:
    - src/test/java/org/specdriven/agent/config/ConfigLoaderTest.java
    - src/test/java/org/specdriven/sdk/SdkBuilderTest.java
---

## ADDED Requirements

### Requirement: Profile-scoped isolation settings are declared explicitly
The system MUST support project-level environment profiles that declare the
observable isolation settings required for profile-backed command execution.

#### Scenario: selected profile preserves isolated home and cache settings
- GIVEN a named environment profile declares an isolated home directory and
  explicit cache-root settings for Maven, npm, Go, and pip
- WHEN repository configuration is loaded and that profile becomes the
  effective selected profile
- THEN the selected profile data MUST preserve those declared isolation
  settings
- AND each preserved setting MUST remain attributable to the selected profile
  name

#### Scenario: selected profile preserves explicit runtime path and env settings
- GIVEN a named environment profile declares a runtime path list and optional
  runtime environment overrides
- WHEN repository configuration is loaded and that profile becomes the
  effective selected profile
- THEN the selected profile data MUST preserve the declared runtime path and
  environment-override settings
- AND those settings MUST remain attributable to the selected profile name

#### Scenario: profile contract supports all four toolchain families
- GIVEN a named environment profile declares bounded toolchain settings for
  JDK, Node.js, Go, and Python
- WHEN repository configuration is loaded through a supported config path
- THEN the configuration contract MUST preserve the declared isolation-relevant
  settings for all four toolchain families
- AND the contract MUST NOT require a second profile namespace for any one
  family

### Requirement: Missing required isolation settings fail explicitly
The system MUST reject a selected environment profile that cannot describe a
usable isolated execution environment.

#### Scenario: selected profile omits required isolation root
- GIVEN repository configuration resolves an effective selected environment
  profile
- AND that selected profile omits the required isolated home directory or one
  of the required explicit cache-root declarations for Maven, npm, Go, or pip
- WHEN a supported repository configuration path resolves that profile for
  isolated execution
- THEN resolution MUST fail explicitly
- AND the failure MUST identify the missing required isolation setting

#### Scenario: invalid isolation field value fails explicitly
- GIVEN a named environment profile declares a home, path, or cache setting
  with an unsupported value shape
- WHEN repository configuration is loaded through a supported config path
- THEN loading MUST fail explicitly
- AND the failure MUST identify the invalid isolation field

#### Scenario: runtime path value must be a non-empty list when declared
- GIVEN a named environment profile declares a runtime path setting
- WHEN the runtime path is empty or contains a blank entry
- THEN loading MUST fail explicitly
- AND the failure MUST identify the invalid runtime path setting
