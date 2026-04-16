---
mapping:
  implementation:
    - src/main/java/org/specdriven/sdk/LealonePlatform.java
  tests:
    - src/test/java/org/specdriven/sdk/LealonePlatformTest.java
---

## MODIFIED Requirements

### Requirement: Sandlock-backed command execution

The system MUST support launching a supported command through the supported
Sandlock entry by using an explicit declared environment profile name or the
already-selected project environment profile.

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

### Requirement: Sandlock pre-launch failures are explicit

The system MUST fail explicitly before command execution when Sandlock-backed
launch prerequisites are not satisfied, and it MUST NOT silently fall back to
direct host execution.

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
