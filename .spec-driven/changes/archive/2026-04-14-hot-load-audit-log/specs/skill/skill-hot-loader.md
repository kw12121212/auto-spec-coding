---
mapping:
  implementation:
    - src/main/java/org/specdriven/skill/hotload/SkillHotLoader.java
    - src/main/java/org/specdriven/skill/hotload/LealoneSkillHotLoader.java
    - src/main/java/org/specdriven/agent/event/Event.java
    - src/main/java/org/specdriven/agent/event/EventBus.java
    - src/main/java/org/specdriven/agent/event/EventType.java
  tests:
    - src/test/java/org/specdriven/skill/hotload/SkillHotLoaderTest.java
    - src/test/java/org/specdriven/agent/event/EventSystemTest.java
---

# skill-hot-loader.md - delta for hot-load-audit-log

## ADDED Requirements

### Requirement: Skill hot-load audit events

- The hot-loader MUST support publishing audit events for hot-load operation attempts
- Hot-load audit events MUST use event type `SKILL_HOT_LOAD_OPERATION`
- Hot-load audit events MUST use event source `skill-hot-loader`
- Audited `load`, `replace`, and `unload` attempts MUST emit exactly one hot-load audit event per attempted public operation
- `load` audit metadata MUST identify operation `load`
- `replace` audit metadata MUST identify operation `replace`
- `unload` audit metadata MUST identify operation `unload`
- Hot-load audit metadata MUST include the skill name and operation result
- Hot-load audit metadata for `load` and `replace` MUST include the source hash
- Hot-load audit metadata MUST NOT include raw Java source text
- Hot-load audit metadata SHOULD include the requester from `PermissionContext` when a requester is available
- Rejected or failed hot-load audit metadata MUST include a stable failure category

### Requirement: Skill hot-load audit outcome coverage

- Disabled `load` and `replace` attempts MUST emit audit metadata showing disabled activation
- Permission-denied or confirmation-required `load`, `replace`, and `unload` attempts MUST emit audit metadata showing permission rejection before exposing the existing permission failure
- Trusted-source rejected `load` and `replace` attempts MUST emit audit metadata showing trust rejection before exposing the existing trusted-source failure
- Duplicate-registration `load` attempts MUST emit audit metadata showing duplicate registration
- Compilation diagnostics failures for `load` and `replace` MUST emit audit metadata showing compile diagnostics failure
- Class-cache or compiler infrastructure failures for `load` and `replace` MUST emit audit metadata showing infrastructure failure before exposing the existing `SkillHotLoaderException`
- Successful `load` and `replace` attempts MUST emit audit metadata showing success and whether the operation used cached class output or compiled source
- `unload` attempts MUST emit audit metadata showing whether an active skill was removed or the operation was a no-op for an absent skill
- Audit publication MUST NOT change existing compile, cache, active-registry, failed-registry, or ClassLoader behavior
- Audit publication MUST NOT make disabled, permission-rejected, or trust-rejected operations perform compile/cache/registry side effects

## ADDED Scenarios

#### Scenario: successful load emits audit event

- GIVEN hot-loading activation is enabled
- AND the permission provider returns `ALLOW` for `skill.hotload.load` on `skill:<skillName>`
- AND the trusted-source policy allows `(skillName, sourceHash)`
- AND hot-load audit events are configured
- WHEN `load(skillName, entryClassName, javaSource, sourceHash)` succeeds
- THEN exactly one `SKILL_HOT_LOAD_OPERATION` event MUST be published
- AND the event metadata MUST include operation `load`, the skill name, the source hash, and a success result
- AND the event metadata MUST NOT include raw Java source text

#### Scenario: trusted-source rejection emits audit event

- GIVEN hot-loading activation is enabled
- AND the permission provider returns `ALLOW` for `skill.hotload.load` on `skill:<skillName>`
- AND the trusted-source policy rejects `(skillName, sourceHash)`
- AND hot-load audit events are configured
- WHEN `load(skillName, entryClassName, javaSource, sourceHash)` is called
- THEN exactly one `SKILL_HOT_LOAD_OPERATION` event MUST be published
- AND the event metadata MUST show operation `load`, trust rejection, the skill name, and the source hash
- AND the compiler MUST NOT be invoked
- AND the class cache MUST NOT be read or populated
- AND `activeLoader(skillName)` MUST return `Optional.empty()`

#### Scenario: permission rejection emits audit event without trust check

- GIVEN hot-loading activation is enabled
- AND the permission provider returns `DENY` or `CONFIRM` for the requested `replace`
- AND hot-load audit events are configured
- WHEN `replace(skillName, entryClassName, javaSource, sourceHash)` is called
- THEN exactly one `SKILL_HOT_LOAD_OPERATION` event MUST be published
- AND the event metadata MUST show operation `replace` and permission rejection
- AND the trusted-source policy MUST NOT be consulted
- AND no compile/cache/registry side effect may occur

#### Scenario: unload emits audit event

- GIVEN hot-loading activation is enabled
- AND the permission provider returns `ALLOW` for `skill.hotload.unload` on `skill:<skillName>`
- AND hot-load audit events are configured
- WHEN `unload(skillName)` is called
- THEN exactly one `SKILL_HOT_LOAD_OPERATION` event MUST be published
- AND the event metadata MUST show operation `unload`, the skill name, and whether an active skill was removed
