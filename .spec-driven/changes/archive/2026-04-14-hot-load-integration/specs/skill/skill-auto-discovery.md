---
mapping:
  implementation:
    - src/main/java/org/specdriven/skill/discovery/DiscoveryResult.java
    - src/main/java/org/specdriven/skill/discovery/SkillAutoDiscovery.java
    - src/main/java/org/specdriven/skill/sql/SkillSqlConverter.java
  tests:
    - src/test/java/org/specdriven/skill/discovery/SkillAutoDiscoveryTest.java
---

# skill-auto-discovery.md - delta for hot-load-integration

## ADDED Requirements

### Requirement: DiscoveryResult hot-load summary

- `DiscoveryResult` MUST add `hotLoadedCount` (int) and `hotLoadFailedCount` (int)
- `hotLoadedCount` MUST count skills whose accompanying Java source was successfully
  loaded into the configured `SkillHotLoader` during discovery
- `hotLoadFailedCount` MUST count skills whose accompanying Java source was attempted
  for hot-load during discovery and returned a failed `SkillLoadResult`
- Existing fields `registeredCount` and `failedCount` MUST retain their current SQL
  registration semantics
- `errors` MUST remain an unmodifiable list and MAY include either SQL registration
  failures or hot-load failures using `SkillDiscoveryError`

### Requirement: SkillAutoDiscovery optional hot-load integration

- `SkillAutoDiscovery` MUST support construction with an optional `SkillHotLoader`
- When no `SkillHotLoader` is configured, `discoverAndRegister()` MUST behave the same
  as the pre-integration implementation
- For each discovered skill directory that contains both `SKILL.md` and an
  accompanying Java source file for the derived executor class, discovery MUST read
  that source, compute a deterministic source hash, and call
  `SkillHotLoader.load(skillName, entryClassName, javaSource, sourceHash)` before SQL
  registration
- If the skill directory does not contain the expected Java source file, discovery MUST
  skip hot-loading and continue with SQL registration
- If `SkillHotLoader.load(...)` returns `success = false`, discovery MUST increment
  `hotLoadFailedCount`, append a `SkillDiscoveryError` for that skill, and continue
  processing remaining skills
- A hot-load failure MUST NOT increment `failedCount` unless SQL registration itself
  also fails
- If hot-loading succeeds, discovery MUST increment `hotLoadedCount`

## ADDED Scenarios

#### Scenario: discovery without hot-loader preserves current behavior

- GIVEN `SkillAutoDiscovery` is constructed without a `SkillHotLoader`
- WHEN `discoverAndRegister()` is called
- THEN SQL registration behavior MUST match the pre-integration implementation
- AND `hotLoadedCount` and `hotLoadFailedCount` MUST both be `0`

#### Scenario: skill with matching Java source is hot-loaded before registration

- GIVEN a discovered skill directory contains `SKILL.md` and the expected executor
  Java source file
- WHEN `discoverAndRegister()` is called with a configured `SkillHotLoader`
- THEN the loader MUST be invoked for that skill before SQL registration
- AND `hotLoadedCount` MUST increase when the load succeeds

#### Scenario: missing Java source skips hot-load

- GIVEN a discovered skill directory contains `SKILL.md` but no expected executor Java
  source file
- WHEN `discoverAndRegister()` is called with a configured `SkillHotLoader`
- THEN discovery MUST skip hot-loading for that skill
- AND SQL registration MUST still proceed normally

#### Scenario: hot-load failure is reported without changing SQL failure count

- GIVEN hot-loading a skill returns `success = false`
- WHEN SQL registration for that same skill succeeds
- THEN `hotLoadFailedCount` MUST increase
- AND `failedCount` MUST remain unchanged for that skill
- AND `errors` MUST include a `SkillDiscoveryError` describing the hot-load failure
