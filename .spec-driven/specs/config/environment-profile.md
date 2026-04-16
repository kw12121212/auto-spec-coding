---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/config/Config.java
    - src/main/java/org/specdriven/agent/config/ConfigLoader.java
    - src/main/java/org/specdriven/sdk/SdkBuilder.java
    - src/main/java/org/specdriven/sdk/SpecDriven.java
  tests:
    - src/test/java/org/specdriven/agent/config/ConfigLoaderTest.java
    - src/test/java/org/specdriven/sdk/SdkBuilderTest.java
---

# Environment Profile

## ADDED Requirements

### Requirement: Project YAML environment profile declaration
The system MUST support project-level environment profile declaration from the repository's YAML configuration surface.

#### Scenario: project config declares named environment profiles
- GIVEN a readable project YAML configuration file
- WHEN the file declares a supported `environmentProfiles` section with one or more named profiles
- THEN the system MUST treat those profile names as selectable project environment profiles
- AND the configuration surface MUST preserve the declared profile names as distinct selectable entries

#### Scenario: project config declares required default profile
- GIVEN a project YAML configuration file that declares environment profiles
- WHEN the configuration is loaded through a supported repository config path
- THEN the configuration MUST declare one default profile name
- AND that default profile name MUST reference one of the declared named profiles

### Requirement: Environment profile selection precedence
The system MUST resolve the effective environment profile using a stable selection order.

#### Scenario: explicit profile selection overrides project default
- GIVEN project YAML declares multiple named environment profiles
- AND one of them is declared as the default profile
- WHEN a supported caller explicitly requests a different declared profile by name
- THEN the system MUST resolve the explicitly requested profile as the effective profile
- AND it MUST NOT silently replace the explicit request with the default profile

#### Scenario: default profile is used when no explicit profile is requested
- GIVEN project YAML declares one or more named environment profiles
- AND one declared profile is marked as the default profile
- WHEN a supported caller does not explicitly request a profile
- THEN the system MUST resolve the declared default profile as the effective profile

### Requirement: Environment profile declaration fields
The first supported environment profile contract MUST allow bounded observable declaration of language runtime and toolchain settings for the repository's supported development stacks.

#### Scenario: profile declares supported toolchain settings
- GIVEN a named environment profile in project YAML
- WHEN the profile declares supported settings for one or more of JDK, Node.js, Go, or Python
- THEN the configuration contract MUST preserve those declared settings for the selected profile
- AND the selected profile data MUST remain attributable to that profile name

#### Scenario: profile omits unrelated toolchain sections
- GIVEN a named environment profile in project YAML
- WHEN the profile declares settings for only a subset of the supported toolchain families
- THEN configuration loading MUST still succeed
- AND omitted toolchain families MUST remain absent rather than being invented implicitly

### Requirement: Missing or unknown profile references fail explicitly
The system MUST reject project configuration that cannot resolve a valid selected environment profile.

#### Scenario: default profile references unknown profile name
- GIVEN project YAML declares environment profiles
- AND the configured default profile name does not match any declared profile
- WHEN the configuration is loaded through a supported repository config path
- THEN loading or assembly MUST fail explicitly
- AND the failure MUST identify the missing default-profile reference

#### Scenario: explicit profile reference is unknown
- GIVEN project YAML declares a valid default profile and one or more named profiles
- WHEN a supported caller explicitly requests a profile name that is not declared
- THEN resolution MUST fail explicitly
- AND the failure MUST identify the unknown requested profile name

### Requirement: Invalid profile content fails explicitly
The system MUST reject invalid environment profile declarations instead of silently falling back to host-environment defaults.

#### Scenario: invalid profile field value fails explicitly
- GIVEN a named environment profile contains an invalid value for a supported profile field
- WHEN the configuration is loaded or profile resolution is attempted through a supported repository path
- THEN the operation MUST fail explicitly
- AND the failure MUST identify the invalid profile field

#### Scenario: no valid fallback to host environment
- GIVEN a project YAML configuration whose `environmentProfiles` section is invalid or cannot resolve a valid selected profile
- WHEN a supported repository configuration path attempts to use that configuration
- THEN the operation MUST fail explicitly
- AND it MUST NOT silently proceed by treating the host environment as an implicit profile

### Requirement: Profile-scoped isolation settings are declared explicitly
The system MUST support project-level environment profiles that declare the observable isolation settings required for profile-backed command execution.

#### Scenario: selected profile preserves isolated home and cache settings
- GIVEN a named environment profile declares an isolated home directory and explicit cache-root settings for Maven, npm, Go, and pip
- WHEN repository configuration is loaded and that profile becomes the effective selected profile
- THEN the selected profile data MUST preserve those declared isolation settings
- AND each preserved setting MUST remain attributable to the selected profile name

#### Scenario: selected profile preserves explicit runtime path and env settings
- GIVEN a named environment profile declares a runtime path list and optional runtime environment overrides
- WHEN repository configuration is loaded and that profile becomes the effective selected profile
- THEN the selected profile data MUST preserve the declared runtime path and environment-override settings
- AND those settings MUST remain attributable to the selected profile name

#### Scenario: profile contract supports all four toolchain families
- GIVEN a named environment profile declares bounded toolchain settings for JDK, Node.js, Go, and Python
- WHEN repository configuration is loaded through a supported config path
- THEN the configuration contract MUST preserve the declared isolation-relevant settings for all four toolchain families
- AND the contract MUST NOT require a second profile namespace for any one family

### Requirement: Missing required isolation settings fail explicitly
The system MUST reject a selected environment profile that cannot describe a usable isolated execution environment.

#### Scenario: selected profile omits required isolation root
- GIVEN repository configuration resolves an effective selected environment profile
- AND that selected profile omits the required isolated home directory or one of the required explicit cache-root declarations for Maven, npm, Go, or pip
- WHEN a supported repository configuration path resolves that profile for isolated execution
- THEN resolution MUST fail explicitly
- AND the failure MUST identify the missing required isolation setting

#### Scenario: invalid isolation field value fails explicitly
- GIVEN a named environment profile declares a home, path, or cache setting with an unsupported value shape
- WHEN repository configuration is loaded through a supported config path
- THEN loading MUST fail explicitly
- AND the failure MUST identify the invalid isolation field

#### Scenario: runtime path value must be a non-empty list when declared
- GIVEN a named environment profile declares a runtime path setting
- WHEN the runtime path is empty or contains a blank entry
- THEN loading MUST fail explicitly
- AND the failure MUST identify the invalid runtime path setting
