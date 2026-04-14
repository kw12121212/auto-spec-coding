---
mapping:
  implementation:
    - src/main/java/org/specdriven/skill/hotload/SkillHotLoaderException.java
    - src/main/java/org/specdriven/skill/hotload/SkillHotLoadPermissionException.java
    - src/main/java/org/specdriven/skill/hotload/SkillLoadResult.java
    - src/main/java/org/specdriven/skill/hotload/SkillHotLoader.java
    - src/main/java/org/specdriven/skill/hotload/LealoneSkillHotLoader.java
  tests:
    - src/test/java/org/specdriven/skill/hotload/SkillHotLoaderTest.java
---

# skill-hot-loader.md - delta for hot-load-permission-guard

## ADDED Requirements

### Requirement: SkillHotLoadPermissionException

- MUST be a runtime exception in the `org.specdriven.skill.hotload` package
- MUST be used when a hot-load operation is rejected because the permission provider returned `DENY` or `CONFIRM`
- MUST include the skill name and requested hot-load action in the exception message
- MUST be distinguishable from `SkillHotLoaderException` failures that represent compiler, cache, registry, or ClassLoader infrastructure problems

### Requirement: Skill hot-load permission guard

- Hot-load `load`, `replace`, and `unload` operations MUST be permission-checked when hot-loading activation is enabled
- The permission check MUST use the existing permission model and MUST preserve the meanings of `ALLOW`, `DENY`, and `CONFIRM`
- `load(skillName, ...)` MUST require permission action `skill.hotload.load` on resource `skill:<skillName>`
- `replace(skillName, ...)` MUST require permission action `skill.hotload.replace` on resource `skill:<skillName>`
- `unload(skillName)` MUST require permission action `skill.hotload.unload` on resource `skill:<skillName>`
- Permission checks for `load` and `replace` MUST occur before compilation, class-cache reads, class-cache writes, active registry mutation, or failed-skill registry mutation
- Permission checks for `unload` MUST occur before active registry mutation or failed-skill registry mutation
- When the permission provider returns `DENY`, the hot-loader MUST reject the operation and MUST NOT perform the protected side effect
- When the permission provider returns `CONFIRM`, the hot-loader MUST reject the operation and MUST NOT perform the protected side effect
- When the permission provider returns `ALLOW`, existing enabled hot-loader behavior MUST proceed unchanged
- When hot-loading activation is disabled, existing disabled behavior MUST remain unchanged and MUST still avoid compile/cache/registry side effects
- When hot-loading activation is enabled but no permission provider or caller permission context is available, the operation MUST fail closed and MUST NOT perform the protected side effect

### Requirement: SkillHotLoader permission failure signal

- Permission-denied and confirmation-required hot-load operations MUST expose a visible failure to callers
- Permission-denied and confirmation-required failures MUST be exposed as `SkillHotLoadPermissionException`
- Permission-denied and confirmation-required failures MUST include the skill name and requested hot-load action in the failure message

## ADDED Scenarios

#### Scenario: authorized load preserves enabled behavior

- GIVEN hot-loading activation is enabled
- AND the permission provider returns `ALLOW` for `skill.hotload.load` on `skill:<skillName>`
- WHEN `load(skillName, entryClassName, javaSource, sourceHash)` is called with valid source
- THEN `SkillLoadResult.success` MUST be `true`
- AND `activeLoader(skillName)` MUST return a non-empty `Optional`

#### Scenario: denied load has no side effects

- GIVEN hot-loading activation is enabled
- AND the permission provider returns `DENY` for `skill.hotload.load` on `skill:<skillName>`
- WHEN `load(skillName, entryClassName, javaSource, sourceHash)` is called
- THEN the operation MUST expose a permission failure
- AND the compiler MUST NOT be invoked
- AND the class cache MUST NOT be read or populated
- AND `activeLoader(skillName)` MUST return `Optional.empty()`
- AND `failedSkillNames()` MUST NOT contain `skillName`

#### Scenario: confirmation-required replace preserves active loader

- GIVEN hot-loading activation is enabled
- AND `skillName` is already registered with a working loader
- AND the permission provider returns `CONFIRM` for `skill.hotload.replace` on `skill:<skillName>`
- WHEN `replace(skillName, entryClassName, javaSource, sourceHash)` is called
- THEN the operation MUST expose a permission failure that indicates confirmation is required
- AND the existing active loader MUST remain unchanged
- AND the compiler MUST NOT be invoked
- AND the class cache MUST NOT be read or populated

#### Scenario: denied unload preserves active loader

- GIVEN hot-loading activation is enabled
- AND `skillName` is already registered with a working loader
- AND the permission provider returns `DENY` for `skill.hotload.unload` on `skill:<skillName>`
- WHEN `unload(skillName)` is called
- THEN the operation MUST expose a permission failure
- AND `activeLoader(skillName)` MUST still return the original loader
- AND `loadedSkillNames()` MUST still contain `skillName`

#### Scenario: missing permission context fails closed

- GIVEN hot-loading activation is enabled
- AND no caller permission context is available for the requested operation
- WHEN a hot-load `load`, `replace`, or `unload` operation is attempted
- THEN the operation MUST expose a permission failure
- AND no compile/cache/registry side effect may occur

#### Scenario: disabled loader behavior remains unchanged

- GIVEN a newly constructed `LealoneSkillHotLoader`
- WHEN a hot-load operation is attempted before explicit enablement
- THEN existing default-disabled behavior MUST be preserved
- AND no permission provider decision is required to avoid compile/cache/registry side effects
