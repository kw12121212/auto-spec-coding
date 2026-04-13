---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/agent/LlmConfigSnapshot.java
    - src/main/java/org/specdriven/agent/agent/LlmProviderRegistry.java
    - src/main/java/org/specdriven/agent/agent/DefaultLlmProviderRegistry.java
    - src/main/java/org/specdriven/agent/event/EventType.java
    - src/main/java/org/specdriven/agent/llm/LealoneRuntimeLlmConfigStore.java
    - src/main/java/org/specdriven/agent/llm/RuntimeLlmConfigStore.java
    - src/main/java/org/specdriven/agent/llm/RuntimeLlmConfigVersion.java
  tests:
    - src/test/java/org/specdriven/agent/agent/DefaultLlmProviderRegistryTest.java
    - src/test/java/org/specdriven/agent/llm/LealoneRuntimeLlmConfigStoreTest.java
---

# Runtime LLM Config

## ADDED Requirements

### Requirement: Default runtime snapshot persistence
The system MUST support persisting the current default runtime LLM config snapshot as a non-sensitive value that survives process restart.

#### Scenario: Persist default runtime snapshot
- GIVEN a default runtime snapshot `S1` is active
- WHEN the system persists the default runtime snapshot
- THEN the persisted record MUST contain the same effective non-sensitive fields as `S1`
- AND no secret field or resolved secret value may be required by this persistence contract

#### Scenario: Session override is not persisted by this change
- GIVEN a session-specific runtime snapshot override exists for `session-a`
- WHEN default runtime snapshot persistence is used
- THEN the persistence contract MUST apply only to the default runtime snapshot
- AND the session-specific override MUST remain outside this change

### Requirement: Restart recovery of last valid persisted default snapshot
The system MUST restore the last valid persisted default runtime snapshot when the runtime config persistence component is initialized again for the same backing store.

#### Scenario: Restart restores latest persisted default snapshot
- GIVEN default runtime snapshot `S1` has been persisted successfully
- WHEN a new runtime config persistence component is created against the same backing store
- THEN it MUST resolve `S1` as the recovered default runtime snapshot

#### Scenario: No persisted default snapshot falls back cleanly
- GIVEN no default runtime snapshot has been persisted yet
- WHEN the runtime config persistence component initializes
- THEN recovery MUST return no persisted snapshot
- AND the runtime registry MAY continue using its non-persisted default behavior

### Requirement: Versioned history for persisted default snapshots
Each successful persistence of the default runtime snapshot MUST create an ordered version history entry so earlier persisted values remain queryable for later restore behavior.

#### Scenario: New persistence creates new version entry
- GIVEN default runtime snapshot `S1` has already been persisted
- AND a later default runtime snapshot `S2` is persisted successfully
- WHEN the persisted history is queried in descending version order
- THEN `S2` MUST appear before `S1`
- AND both versions MUST remain available in the history

#### Scenario: Failed persistence does not publish partial history entry
- GIVEN default runtime snapshot `S1` is already the latest persisted version
- WHEN a later persistence attempt for `S2` fails before completion
- THEN the latest persisted version MUST remain `S1`
- AND no partial or unreadable history entry for `S2` may become observable

### Requirement: Internal restore from persisted version history
The system MUST support internally restoring the active persisted default runtime snapshot from an earlier persisted version without deleting the recorded history.

#### Scenario: Restore earlier version makes it active again
- GIVEN persisted default runtime snapshot versions `S1`, `S2`, and `S3` exist in order from oldest to newest
- WHEN the system restores `S1` as the active persisted default snapshot
- THEN later default runtime snapshot resolution MUST return the restored `S1` values
- AND the version history MUST remain queryable after the restore

#### Scenario: Restore does not require new external command surface
- GIVEN internal restore behavior is supported
- WHEN this change defines restore semantics
- THEN it MUST NOT require a new SQL, HTTP, or SDK command surface in this change

### Requirement: Persistence does not change existing request binding semantics
Persisting and recovering the default runtime snapshot MUST NOT change the existing in-flight request binding behavior defined for runtime snapshots.

#### Scenario: Recovery affects only later requests
- GIVEN a request started before a recovered or restored default snapshot becomes active
- WHEN that request continues to completion
- THEN it MUST keep using the snapshot it originally resolved
- AND only later requests MAY observe the recovered or restored default snapshot
