---
mapping:
  implementation:
    - src/main/java/org/specdriven/skill/hotload/SkillHotLoaderException.java
    - src/main/java/org/specdriven/skill/hotload/SkillLoadResult.java
    - src/main/java/org/specdriven/skill/hotload/SkillHotLoader.java
    - src/main/java/org/specdriven/skill/hotload/LealoneSkillHotLoader.java
  tests:
    - src/test/java/org/specdriven/skill/hotload/SkillHotLoaderTest.java
---

# skill-hot-loader.md - delta for hot-load-default-disabled

## MODIFIED Requirements

### Requirement: SkillHotLoader interface

- `SkillHotLoader` MUST provide programmatic enablement state for dynamic activation instead of assuming hot-loading is always active once constructed
- `SkillHotLoader` MUST expose whether hot-loading activation is currently enabled so callers can make deterministic activation decisions
- `SkillHotLoader` MUST default to disabled activation unless the constructing code path explicitly enables it

#### Requirement: load

- When hot-loading activation is disabled, `load(...)` MUST return `success = false`
- When hot-loading activation is disabled, `load(...)` MUST NOT compile source, MUST NOT read or populate the class cache, and MUST NOT register an active loader entry
- When hot-loading activation is disabled, `load(...)` MUST NOT add the skill name to `failedSkillNames()`
- When hot-loading activation is enabled, existing compile-or-cache-hit behavior remains unchanged

#### Requirement: replace

- When hot-loading activation is disabled, `replace(...)` MUST return `success = false`
- When hot-loading activation is disabled, `replace(...)` MUST NOT compile source, MUST NOT read or populate the class cache, and MUST NOT modify any existing active loader entry
- When hot-loading activation is disabled, `replace(...)` MUST NOT add the skill name to `failedSkillNames()`
- When hot-loading activation is enabled, existing replacement behavior remains unchanged

### Requirement: LealoneSkillHotLoader

- `LealoneSkillHotLoader` MUST start with hot-loading activation disabled by default
- `LealoneSkillHotLoader` MUST support explicit programmatic enablement by the constructing code path
- `LealoneSkillHotLoader` MUST preserve current registry, cache, and class-loader behavior when activation has been explicitly enabled

## ADDED Scenarios

#### Scenario: default-disabled loader rejects load activation

- GIVEN a newly constructed `LealoneSkillHotLoader`
- WHEN `load(skillName, entryClassName, javaSource, sourceHash)` is called before explicit enablement
- THEN `SkillLoadResult.success` MUST be `false`
- AND `activeLoader(skillName)` MUST return `Optional.empty()`
- AND `loadedSkillNames()` MUST NOT contain `skillName`
- AND `failedSkillNames()` MUST NOT contain `skillName`

#### Scenario: default-disabled loader rejects replace activation

- GIVEN a newly constructed `LealoneSkillHotLoader`
- WHEN `replace(skillName, entryClassName, javaSource, sourceHash)` is called before explicit enablement
- THEN `SkillLoadResult.success` MUST be `false`
- AND `activeLoader(skillName)` MUST return `Optional.empty()`
- AND `failedSkillNames()` MUST NOT contain `skillName`

#### Scenario: explicit programmatic enablement restores normal load behavior

- GIVEN a `LealoneSkillHotLoader` whose constructing code path explicitly enabled hot-loading activation
- WHEN `load(skillName, entryClassName, javaSource, sourceHash)` is called with valid source
- THEN `SkillLoadResult.success` MUST be `true`
- AND `activeLoader(skillName)` MUST return a non-empty `Optional`
- AND existing cache and registry semantics MUST remain unchanged
