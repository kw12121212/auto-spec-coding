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
