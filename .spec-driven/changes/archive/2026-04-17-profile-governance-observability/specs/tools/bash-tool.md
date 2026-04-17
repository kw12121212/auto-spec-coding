---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/tool/BashTool.java
    - src/main/java/org/specdriven/agent/tool/ProfileBoundCommandExecutor.java
    - src/main/java/org/specdriven/sdk/LealonePlatform.java
  tests:
    - src/test/java/org/specdriven/agent/tool/BashToolTest.java
    - src/test/java/org/specdriven/sdk/SdkAgentTest.java
    - src/test/java/org/specdriven/sdk/LealonePlatformTest.java
---

## MODIFIED Requirements

### Requirement: BashTool permission integration
Previously: `BashTool` MUST call `ToolContext.permissionProvider().check()` before execution.
`BashTool` MUST call `ToolContext.permissionProvider().check()` before any execution path begins, including both direct-host execution and Sandlock-backed profile execution.

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
