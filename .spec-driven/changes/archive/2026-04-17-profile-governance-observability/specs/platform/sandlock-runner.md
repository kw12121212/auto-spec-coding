---
mapping:
  implementation:
    - src/main/java/org/specdriven/sdk/LealonePlatform.java
    - src/main/java/org/specdriven/agent/tool/ProfileBoundCommandExecutor.java
    - src/main/java/org/specdriven/agent/tool/BashTool.java
  tests:
    - src/test/java/org/specdriven/sdk/LealonePlatformTest.java
    - src/test/java/org/specdriven/sdk/PlatformHealthTest.java
    - src/test/java/org/specdriven/agent/tool/BashToolTest.java
---

## ADDED Requirements

### Requirement: auditable Sandlock-backed execution
The system MUST publish or record a minimal audit event for every supported Sandlock-backed execution attempt.

#### Scenario: successful profile-backed execution emits audit metadata
- GIVEN a platform assembled with an EventBus
- AND a Sandlock-backed command launches under resolved profile `dev`
- WHEN the command completes
- THEN the system MUST publish or record an audit event for that attempt
- AND the audit metadata MUST identify `dev` as the resolved profile
- AND it MUST identify the executed command in a stable rendered form
- AND it MUST include the process outcome and exit code

#### Scenario: pre-launch failure emits diagnostic audit metadata
- GIVEN a caller requests Sandlock-backed execution
- AND the attempt fails before the command starts because the host is unsupported, the Sandlock entry is unavailable, the requested profile is unknown, or the selected profile is invalid
- WHEN the failure is returned to the caller
- THEN the system MUST publish or record an audit event for the failed attempt
- AND the audit metadata MUST include a stable failure code
- AND it MUST include the requested profile name when the caller supplied one
- AND it MUST NOT report the failed attempt as a completed command execution

## MODIFIED Requirements

### Requirement: Sandlock pre-launch failures are explicit
Previously: The system MUST fail explicitly before command execution when Sandlock-backed launch prerequisites are not satisfied, and it MUST NOT silently fall back to direct host execution.
The system MUST fail explicitly before command execution when Sandlock-backed launch prerequisites are not satisfied, it MUST return a stable failure code with a diagnostic message, and it MUST NOT silently fall back to direct host execution.

#### Scenario: unsupported host failure exposes stable diagnostic code
- GIVEN the current host environment does not satisfy the supported Sandlock runtime contract
- WHEN a caller requests Sandlock-backed command execution
- THEN the operation MUST fail explicitly
- AND the failure MUST identify stable failure code `UNSUPPORTED_HOST`
- AND the failure MUST include a diagnostic message explaining why the host is unsupported
