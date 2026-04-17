---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/tool/BackgroundProcessHandle.java
    - src/main/java/org/specdriven/agent/tool/ProcessManager.java
    - src/main/java/org/specdriven/agent/tool/DefaultProcessManager.java
  tests:
    - src/test/java/org/specdriven/agent/tool/BackgroundProcessHandleTest.java
    - src/test/java/org/specdriven/agent/tool/BackgroundToolIntegrationTest.java
    - src/test/java/org/specdriven/agent/tool/ProcessManagerTest.java
---

## ADDED Requirements

### Requirement: ProcessManager resolved-profile registration
`ProcessManager` MUST support preserving a resolved repository environment profile name when the launch surface already knows that the background process was started under a profile-bound execution path.

#### Scenario: registration preserves resolved profile name
- GIVEN a background-process launch surface has already resolved an effective repository environment profile
- WHEN it registers the started process with `ProcessManager`
- THEN the returned `BackgroundProcessHandle` MUST expose the same resolved profile name

#### Scenario: lifecycle updates preserve resolved profile name
- GIVEN a background process is registered with a resolved repository environment profile name
- WHEN the process later appears through process-manager lifecycle updates
- THEN the updated `BackgroundProcessHandle` MUST preserve the same resolved profile name

#### Scenario: registration without profile keeps null metadata
- GIVEN a background process is registered without any resolved repository environment profile
- WHEN the launch returns or later lists a `BackgroundProcessHandle`
- THEN `resolvedProfile` MUST be null

## MODIFIED Requirements

### Requirement: BackgroundProcessHandle record
Previously: `BackgroundProcessHandle` MUST be a Java record in `org.specdriven.agent.tool` with fields: `id` (String, UUID), `pid` (long, OS process ID, -1 if unavailable), `command` (String), `toolName` (String), `startTime` (long, epoch millis), `state` (ProcessState).
`BackgroundProcessHandle` MUST be a Java record in `org.specdriven.agent.tool` with fields: `id` (String, UUID), `pid` (long, OS process ID, -1 if unavailable), `command` (String), `toolName` (String), `startTime` (long, epoch millis), `state` (ProcessState), and `resolvedProfile` (String, nullable).

#### Scenario: handle preserves resolved profile name
- GIVEN a background process was launched under a resolved repository environment profile
- WHEN the launch returns a `BackgroundProcessHandle`
- THEN `resolvedProfile` MUST equal the declared profile name used for that launch

#### Scenario: handle preserves absence of profile binding
- GIVEN a background process was launched without any resolved repository environment profile
- WHEN the launch returns a `BackgroundProcessHandle`
- THEN `resolvedProfile` MUST be null
