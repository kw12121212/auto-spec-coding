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

# Bash Tool Spec

## ADDED Requirements

### Requirement: BashTool identity

- MUST return `"bash"` from `getName()`
- MUST return a non-empty description from `getDescription()`
- MUST declare parameters: `command` (string, required), `timeout` (integer, optional), `workDir` (string, optional), `profile` (string, optional)

### Requirement: BashTool execution

- MUST execute the `command` parameter via the system shell while honoring the resolved environment-profile selection rules for the current repository when a profile-backed execution path is available
- MUST capture and return the combined stdout and stderr output in `ToolResult.Success`
- MUST use the `workDir` parameter as working directory when provided; otherwise MUST use `ToolContext.workDir()`
- MUST use the `timeout` parameter (in seconds) when provided; otherwise MUST default to 120 seconds
- MUST terminate the process and return `ToolResult.Error` with a timeout message if the process exceeds the configured timeout
- MUST use `ProcessHandle.destroyForcibly()` for timeout termination

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

### Requirement: BashTool permission integration

- MUST call `ToolContext.permissionProvider().check()` before execution
- MUST construct the Permission with `action="execute"`, `resource="bash"`, and `constraints` containing the command string
- MUST return `ToolResult.Error` without executing when the permission decision is `PermissionDecision.DENY`
- MUST return `ToolResult.Error` without executing when the permission decision is `PermissionDecision.CONFIRM`, and the error message MUST indicate that explicit confirmation is required

#### Scenario: denied profile-backed bash request is rejected before launch
- GIVEN the current repository configuration would otherwise resolve a supported environment profile for the command
- AND the permission provider returns `DENY`
- WHEN the caller executes `BashTool`
- THEN `BashTool` MUST return a permission-denied error
- AND it MUST NOT start either a direct-host or Sandlock-backed command process

#### Scenario: confirmation-required profile-backed bash request is rejected before launch
- GIVEN the current repository configuration would otherwise resolve a supported environment profile for the command
- AND the permission provider returns `CONFIRM`
- WHEN the caller executes `BashTool`
- THEN `BashTool` MUST return an error indicating explicit confirmation is required
- AND it MUST NOT start either a direct-host or Sandlock-backed command process

### Requirement: BashTool error handling

- MUST return `ToolResult.Error` if `command` parameter is missing or empty
- MUST return `ToolResult.Error` if the process fails to start
- MUST return `ToolResult.Error` with the process exit message if the process exits with a non-zero code
