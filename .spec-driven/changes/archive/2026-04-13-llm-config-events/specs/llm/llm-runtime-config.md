---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/agent/LlmProviderRegistry.java
    - src/main/java/org/specdriven/agent/agent/DefaultLlmProviderRegistry.java
    - src/main/java/org/specdriven/agent/event/Event.java
    - src/main/java/org/specdriven/agent/event/EventBus.java
    - src/main/java/org/specdriven/agent/event/EventType.java
    - src/main/java/org/specdriven/sdk/SdkBuilder.java
  tests:
    - src/test/java/org/specdriven/agent/agent/DefaultLlmProviderRegistryTest.java
    - src/test/java/org/specdriven/agent/event/EventSystemTest.java
    - src/test/java/org/specdriven/sdk/SdkBuilderTest.java
    - src/test/java/org/specdriven/sdk/SdkBuilderEventTest.java
---

# Runtime LLM Config

## ADDED Requirements

### Requirement: Successful runtime config changes publish `LLM_CONFIG_CHANGED`
The system MUST publish one `EventType.LLM_CONFIG_CHANGED` event through the configured `EventBus` whenever a successful runtime LLM config change becomes active for future requests.

#### Scenario: Successful default snapshot replacement publishes one event
- GIVEN a runtime LLM registry with an `EventBus`
- AND a default runtime snapshot `S1` is currently active
- WHEN a successful default snapshot replacement makes `S2` active for later requests
- THEN exactly one `LLM_CONFIG_CHANGED` event MUST be published
- AND the event MUST describe the post-change default scope rather than the previous snapshot

#### Scenario: Successful session snapshot replacement publishes one session-scoped event
- GIVEN a runtime LLM registry with an `EventBus`
- AND session `session-a` currently resolves runtime snapshot `S1`
- WHEN a successful session snapshot replacement makes `S2` active for later requests in `session-a`
- THEN exactly one `LLM_CONFIG_CHANGED` event MUST be published
- AND the event MUST identify the affected session scope

#### Scenario: Successful `SET LLM` publishes one session-scoped event
- GIVEN a runtime LLM registry with an `EventBus`
- AND session `session-a` currently resolves runtime snapshot `S1`
- WHEN a successful `SET LLM` statement installs replacement snapshot `S2` for later requests in `session-a`
- THEN exactly one `LLM_CONFIG_CHANGED` event MUST be published
- AND the event MUST describe the committed post-statement session snapshot

#### Scenario: Clearing a session override publishes one fallback event
- GIVEN a runtime LLM registry with an `EventBus`
- AND session `session-a` currently resolves a session-specific runtime snapshot override
- WHEN that session override is cleared successfully and the session falls back to the default runtime snapshot
- THEN exactly one `LLM_CONFIG_CHANGED` event MUST be published
- AND the event MUST describe the post-clear effective session snapshot

#### Scenario: Failed runtime update publishes no success event
- GIVEN a runtime LLM registry with an `EventBus`
- AND a runtime config update attempt fails before a replacement snapshot becomes active
- WHEN the failure is returned to the caller
- THEN no `LLM_CONFIG_CHANGED` event may be published for that failed attempt

### Requirement: `LLM_CONFIG_CHANGED` metadata identifies scope and affected fields
Each published `LLM_CONFIG_CHANGED` event MUST carry enough non-sensitive metadata for downstream consumers to identify which scope changed and which runtime fields changed.

#### Scenario: Default-scope event metadata
- GIVEN a successful default runtime snapshot replacement publishes `LLM_CONFIG_CHANGED`
- WHEN the event metadata is inspected
- THEN it MUST contain `scope = "default"`
- AND it MUST contain the effective post-change `provider`
- AND it MUST contain `changedKeys` as a string value naming the non-sensitive runtime fields whose effective values changed
- AND it MUST NOT contain `sessionId`

#### Scenario: Session-scope event metadata
- GIVEN a successful session-scoped runtime config change publishes `LLM_CONFIG_CHANGED`
- WHEN the event metadata is inspected
- THEN it MUST contain `scope = "session"`
- AND it MUST contain the affected `sessionId`
- AND it MUST contain the effective post-change `provider`
- AND it MUST contain `changedKeys` as a string value naming the non-sensitive runtime fields whose effective values changed

#### Scenario: Event metadata excludes full snapshot payloads
- GIVEN a published `LLM_CONFIG_CHANGED` event
- WHEN the event metadata is inspected
- THEN it MUST identify the affected scope and changed fields without embedding the full pre-change or post-change snapshot as metadata
- AND it MUST remain limited to non-sensitive runtime config information

### Requirement: Config-change events preserve existing request binding semantics
Publishing `LLM_CONFIG_CHANGED` MUST NOT alter the existing runtime config semantics for atomic replacement, session isolation, or in-flight request snapshot binding.

#### Scenario: Event publication does not change in-flight request binding
- GIVEN an in-flight LLM request is already bound to runtime snapshot `S1`
- AND a later successful runtime config change publishes `LLM_CONFIG_CHANGED` for replacement snapshot `S2`
- WHEN the in-flight request continues to completion
- THEN it MUST continue using `S1`
- AND only later requests MAY observe `S2`
