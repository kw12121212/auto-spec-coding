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

### Requirement: Sandlock-backed execution uses profile-scoped isolation
The system MUST launch a Sandlock-backed command by using the selected or
explicitly requested profile's isolated home, executable-search path, and
explicit tool-cache settings instead of unrelated host defaults.

#### Scenario: selected profile applies isolated home and cache roots
- GIVEN a platform assembled from project YAML whose effective selected
  environment profile declares an isolated home directory and explicit cache
  roots for Maven, npm, Go, and pip
- AND Sandlock is available in the supported host environment
- WHEN a caller requests Sandlock-backed command execution without overriding
  the profile
- THEN the launched command MUST use the selected profile's declared isolated
  home directory
- AND it MUST use the selected profile's declared cache roots instead of
  inheriting unrelated host-default Maven/npm/Go/pip state

#### Scenario: explicit profile applies four-family toolchain lookup
- GIVEN a platform assembled from project YAML that declares an environment
  profile with bounded toolchain settings for JDK, Node.js, Go, and Python
- AND that profile declares an explicit runtime path setting
- AND Sandlock is available in the supported host environment
- WHEN a caller requests Sandlock-backed command execution with that explicit
  profile
- THEN the launched command MUST use a profile-scoped executable-search path
  from the declared runtime path setting
- AND it MUST NOT silently rely on unrelated host-global toolchain directories

### Requirement: Invalid isolated profile fails before launch
The system MUST fail before command execution when the selected or requested
profile cannot provide the required isolated execution environment.

#### Scenario: requested profile has missing isolation settings
- GIVEN a platform assembled from project YAML that declares environment
  profiles
- AND the selected or explicitly requested profile is missing the required
  isolated home directory or one of the required explicit Maven/npm/Go/pip
  cache-root settings
- WHEN a caller requests Sandlock-backed command execution
- THEN the operation MUST fail explicitly before command launch
- AND the failure MUST identify the missing isolation setting

#### Scenario: requested profile has invalid toolchain-isolation values
- GIVEN a platform assembled from project YAML that declares environment
  profiles
- AND the selected or explicitly requested profile contains invalid isolation
  settings for home, executable-search path resolution, or bounded toolchain
  lookup
- WHEN a caller requests Sandlock-backed command execution
- THEN the operation MUST fail explicitly before command launch
- AND the failure MUST identify the invalid isolation setting
