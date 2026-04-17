---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/tool/BashTool.java
    - src/main/java/org/specdriven/agent/tool/ProfileBoundCommandExecutor.java
    - src/main/java/org/specdriven/agent/agent/DefaultOrchestrator.java
    - src/main/java/org/specdriven/sdk/SpecDriven.java
    - src/main/java/org/specdriven/sdk/SdkAgent.java
    - src/main/java/org/specdriven/sdk/SdkBuilder.java
    - src/main/java/org/specdriven/sdk/LealonePlatform.java
  tests:
    - src/test/java/org/specdriven/agent/tool/BashToolTest.java
    - src/test/java/org/specdriven/sdk/SdkAgentTest.java
    - src/test/java/org/specdriven/sdk/SdkBuilderTest.java
    - src/test/java/org/specdriven/sdk/LealonePlatformTest.java
---

## MODIFIED Requirements

### Requirement: BashTool identity
Previously: `BashTool` MUST declare parameters: `command` (string, required), `timeout` (integer, optional), `workDir` (string, optional).
`BashTool` MUST declare parameters: `command` (string, required), `timeout` (integer, optional), `workDir` (string, optional), and `profile` (string, optional).

### Requirement: BashTool execution
Previously: `BashTool` MUST execute the `command` parameter via the system shell (`/bin/bash -c` on Linux, `sh -c` as fallback).
`BashTool` MUST execute the `command` parameter via the system shell while honoring the resolved environment-profile selection rules for the current repository when a profile-backed execution path is available.

#### Scenario: explicit bash profile request is honored
- GIVEN the current repository declares named environment profiles
- AND Sandlock-backed execution is available for the current host
- WHEN the caller executes `BashTool` with `profile` set to a declared profile name
- THEN the command MUST run under that declared profile
- AND `BashTool` MUST NOT silently replace the explicit request with the repository default profile

#### Scenario: omitted bash profile uses selected project profile
- GIVEN the current repository configuration available to the tool resolves an effective selected environment profile
- AND Sandlock-backed execution is available for the current host
- WHEN the caller executes `BashTool` without the optional `profile` parameter
- THEN the command MUST run under the effective selected environment profile

#### Scenario: unknown explicit bash profile fails explicitly
- GIVEN the current repository does not declare the requested `profile` name
- WHEN the caller executes `BashTool` with that explicit `profile`
- THEN `BashTool` MUST fail explicitly
- AND the failure MUST identify the unknown requested profile name

#### Scenario: resolved profile does not fall back to direct host execution
- GIVEN `BashTool` resolves either an explicit requested profile or the effective selected repository profile
- AND profile-backed execution prerequisites are not satisfied
- WHEN the caller executes the command
- THEN `BashTool` MUST fail explicitly
- AND it MUST NOT run the command directly on the host as a fallback

#### Scenario: repository without environment profiles keeps existing host execution behavior
- GIVEN the current repository configuration available to the tool does not resolve any environment profile for `BashTool` execution
- WHEN the caller executes `BashTool` without the optional `profile` parameter
- THEN `BashTool` MUST keep the existing direct host shell execution behavior
