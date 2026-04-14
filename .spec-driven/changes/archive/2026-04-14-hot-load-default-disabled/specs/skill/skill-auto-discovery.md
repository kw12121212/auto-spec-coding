---
mapping:
  implementation:
    - src/main/java/org/specdriven/skill/discovery/DiscoveryResult.java
    - src/main/java/org/specdriven/skill/discovery/SkillAutoDiscovery.java
  tests:
    - src/test/java/org/specdriven/skill/discovery/SkillAutoDiscoveryTest.java
---

# skill-auto-discovery.md - delta for hot-load-default-disabled

## MODIFIED Requirements

### Requirement: SkillAutoDiscovery

- When a `SkillHotLoader` is configured but hot-loading activation is disabled, discovery MUST continue processing skills instead of treating disabled activation as a directory-level failure
- When a `SkillHotLoader` is configured but hot-loading activation is disabled, discovery MUST leave SQL registration behavior unchanged
- When a discovered skill has matching Java source but hot-loading activation is disabled, discovery MUST report the hot-load attempt as a per-skill hot-load failure and continue processing remaining skills

## ADDED Scenarios

#### Scenario: disabled hot-loader does not block SQL registration

- GIVEN a discovered skill directory contains `SKILL.md` and the expected executor Java source file
- AND `SkillAutoDiscovery` is constructed with a `SkillHotLoader` whose activation is still disabled
- WHEN `discoverAndRegister()` is called
- THEN SQL registration for that skill MUST still proceed normally
- AND `registeredCount` MUST increase for the successful SQL registration
- AND `hotLoadFailedCount` MUST increase for the disabled activation attempt

#### Scenario: disabled hot-loader failure is reported as a hot-load error only

- GIVEN a discovered skill directory contains matching Java source
- AND the configured `SkillHotLoader` remains disabled
- WHEN `discoverAndRegister()` is called
- THEN `errors` MUST include a `SkillDiscoveryError` describing that hot-loading is disabled
- AND `failedCount` MUST remain unchanged unless SQL registration itself also fails

#### Scenario: no hot-loader still preserves current discovery behavior

- GIVEN `SkillAutoDiscovery` is constructed without a `SkillHotLoader`
- WHEN `discoverAndRegister()` is called
- THEN discovery MUST behave the same as before this change
- AND no disabled-hot-load error may be reported
