---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/loop/CommandSpecDrivenPhaseRunner.java
    - src/main/java/org/specdriven/agent/tool/ProfileBoundCommandExecutor.java
    - src/main/java/org/specdriven/agent/loop/SpecDrivenPipeline.java
  tests:
    - src/test/java/org/specdriven/agent/loop/SpecDrivenPipelineTest.java
---

## MODIFIED Requirements

### Requirement: Command-backed spec-driven phase runner
Previously: commands MUST run with `LoopConfig.projectRoot()` as their working directory.
Commands MUST run with `LoopConfig.projectRoot()` as their working directory, and when the loop's supported configuration source resolves an effective selected repository environment profile for that project root, the command runner MUST execute each external phase command under that selected project profile.

#### Scenario: selected project profile is used for command-backed phases
- GIVEN `LoopConfig.projectRoot()` and the loop's supported configuration source resolve an effective selected environment profile
- AND profile-backed execution is available for the current host
- WHEN `CommandSpecDrivenPhaseRunner` runs an external workflow phase command
- THEN the phase command MUST execute under the resolved selected repository profile

#### Scenario: loop phases do not add a separate override profile
- GIVEN command-backed autonomous loop execution is configured for a repository with a selected environment profile
- WHEN an external workflow phase command runs
- THEN the command runner MUST use the resolved project/default profile as the binding source
- AND this change MUST NOT require a loop-specific explicit profile override surface

#### Scenario: repository without environment profiles keeps existing loop command behavior
- GIVEN the loop's supported configuration source does not resolve any environment profile for command-backed phase execution
- WHEN `CommandSpecDrivenPhaseRunner` runs an external workflow phase command
- THEN the phase command MUST keep the existing direct command execution behavior

#### Scenario: resolved loop profile does not fall back to direct host execution
- GIVEN the loop's supported configuration source resolves an effective selected environment profile
- AND profile-backed execution prerequisites are not satisfied
- WHEN `CommandSpecDrivenPhaseRunner` attempts to run an external workflow phase command
- THEN the phase MUST fail explicitly
- AND the command runner MUST NOT run that phase command directly on the host as a fallback
