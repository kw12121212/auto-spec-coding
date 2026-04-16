---
mapping:
  implementation:
    - src/main/java/org/specdriven/sdk/LealonePlatform.java
    - src/main/java/org/specdriven/sdk/SdkBuilder.java
  tests:
    - src/test/java/org/specdriven/sdk/LealonePlatformTest.java
    - src/test/java/org/specdriven/sdk/SdkBuilderTest.java
---

# Sandlock Runner

## ADDED Requirements

### Requirement: Sandlock-backed command execution

The system MUST support launching a supported command through the supported
Sandlock entry by using an explicit declared environment profile name or the
already-selected project environment profile.

#### Scenario: explicit declared profile is used for launch
- GIVEN a platform assembled from project YAML that declares environment
  profiles
- AND Sandlock is available in the current supported host environment
- WHEN a caller requests Sandlock-backed execution with an explicit declared
  profile name
- THEN the command MUST be launched through the supported Sandlock entry using
  that declared profile name
- AND the returned execution result MUST identify the same resolved profile name

#### Scenario: selected project profile is used when no explicit profile is requested
- GIVEN a platform assembled from project YAML whose effective selected
  environment profile is `dev`
- AND Sandlock is available in the current supported host environment
- WHEN a caller requests Sandlock-backed execution without an explicit profile
  name
- THEN the command MUST be launched using the selected project profile `dev`
- AND the returned execution result MUST identify `dev` as the resolved profile

#### Scenario: repository-bundled Sandlock entry is used by default
- GIVEN the current host environment is Linux x86_64 and satisfies the
  supported Sandlock runtime contract
- AND the repository-bundled pinned Sandlock entry is present and executable
- AND the caller does not set `SPEC_DRIVEN_SANDLOCK_ENTRY`
- WHEN a caller requests Sandlock-backed command execution
- THEN the system MUST use the repository-bundled Sandlock entry by default
- AND the default supported execution path MUST NOT require a separate host
  `PATH` installation of `sandlock`

#### Scenario: explicit Sandlock entry override takes precedence
- GIVEN a caller sets `SPEC_DRIVEN_SANDLOCK_ENTRY` to an executable Sandlock
  binary path
- AND the repository-bundled Sandlock entry is also present
- WHEN a caller requests Sandlock-backed command execution
- THEN the system MUST use the explicitly configured override path
- AND it MUST NOT silently replace that explicit override with the bundled copy

### Requirement: Structured Sandlock execution result

The system MUST return a structured result for a launched Sandlock-backed
command rather than collapsing process outcome into an unstructured string.

#### Scenario: successful command preserves structured outcome
- GIVEN a supported command launches successfully through Sandlock and exits with
  code `0`
- WHEN the command completes
- THEN the execution result MUST include the resolved profile name
- AND it MUST include the executed command arguments
- AND it MUST include exit code `0`
- AND it MUST include captured stdout and stderr output

#### Scenario: non-zero process exit remains observable
- GIVEN a supported command launches successfully through Sandlock but exits
  with a non-zero code
- WHEN the command completes
- THEN the execution result MUST preserve the non-zero exit code
- AND it MUST preserve any captured stdout and stderr output
- AND the command MUST NOT be reported as if it never launched

### Requirement: Sandlock pre-launch failures are explicit

The system MUST fail explicitly before command execution when Sandlock-backed
launch prerequisites are not satisfied, and it MUST NOT silently fall back to
direct host execution.

#### Scenario: missing Sandlock entry fails explicitly
- GIVEN the current host environment does not provide the supported Sandlock
  entry
- WHEN a caller requests Sandlock-backed command execution
- THEN the operation MUST fail explicitly
- AND the failure MUST identify that Sandlock is unavailable
- AND the command MUST NOT be run directly on the host as a fallback

#### Scenario: missing bundled Sandlock entry fails explicitly
- GIVEN the current host environment is otherwise supported for Sandlock-backed
  execution
- AND `SPEC_DRIVEN_SANDLOCK_ENTRY` is not set
- AND the repository-bundled pinned Sandlock entry is missing or not executable
- WHEN a caller requests Sandlock-backed command execution
- THEN the operation MUST fail explicitly
- AND the failure MUST identify that the repository-bundled Sandlock entry is
  unavailable
- AND the command MUST NOT be run directly on the host as a fallback

#### Scenario: unsupported host environment fails explicitly
- GIVEN the current host environment does not satisfy the supported Sandlock
  runtime contract
- WHEN a caller requests Sandlock-backed command execution
- THEN the operation MUST fail explicitly
- AND the failure MUST identify that the host environment is unsupported for
  Sandlock-backed execution

#### Scenario: unknown explicit profile fails explicitly
- GIVEN a platform assembled from project YAML that declares environment
  profiles
- WHEN a caller requests Sandlock-backed execution with an explicit profile name
  that is not declared
- THEN the operation MUST fail explicitly
- AND the failure MUST identify the unknown requested profile name

#### Scenario: no effective profile is available
- GIVEN a platform instance that does not have a resolved project environment
  profile available for Sandlock-backed execution
- WHEN a caller omits the explicit profile name
- THEN the operation MUST fail explicitly
- AND the failure MUST identify that no effective environment profile is
  available for Sandlock-backed execution

### Requirement: Sandlock-backed execution uses profile-scoped isolation
The system MUST launch a Sandlock-backed command by using the selected or explicitly requested profile's isolated home, executable-search path, and explicit tool-cache settings instead of unrelated host defaults.

#### Scenario: selected profile applies isolated home and cache roots
- GIVEN a platform assembled from project YAML whose effective selected environment profile declares an isolated home directory and explicit cache roots for Maven, npm, Go, and pip
- AND Sandlock is available in the supported host environment
- WHEN a caller requests Sandlock-backed command execution without overriding the profile
- THEN the launched command MUST use the selected profile's declared isolated home directory
- AND it MUST use the selected profile's declared cache roots instead of inheriting unrelated host-default Maven/npm/Go/pip state

#### Scenario: explicit profile applies four-family toolchain lookup
- GIVEN a platform assembled from project YAML that declares an environment profile with bounded toolchain settings for JDK, Node.js, Go, and Python
- AND that profile declares an explicit runtime path setting
- AND Sandlock is available in the supported host environment
- WHEN a caller requests Sandlock-backed command execution with that explicit profile
- THEN the launched command MUST use a profile-scoped executable-search path from the declared runtime path setting
- AND it MUST NOT silently rely on unrelated host-global toolchain directories

### Requirement: Invalid isolated profile fails before launch
The system MUST fail before command execution when the selected or requested profile cannot provide the required isolated execution environment.

#### Scenario: requested profile has missing isolation settings
- GIVEN a platform assembled from project YAML that declares environment profiles
- AND the selected or explicitly requested profile is missing the required isolated home directory or one of the required explicit Maven/npm/Go/pip cache-root settings
- WHEN a caller requests Sandlock-backed command execution
- THEN the operation MUST fail explicitly before command launch
- AND the failure MUST identify the missing isolation setting

#### Scenario: requested profile has invalid toolchain-isolation values
- GIVEN a platform assembled from project YAML that declares environment profiles
- AND the selected or explicitly requested profile contains invalid isolation settings for home, executable-search path resolution, or bounded toolchain lookup
- WHEN a caller requests Sandlock-backed command execution
- THEN the operation MUST fail explicitly before command launch
- AND the failure MUST identify the invalid isolation setting
