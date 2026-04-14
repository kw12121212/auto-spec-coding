---
mapping:
  implementation:
    - src/main/java/org/specdriven/skill/discovery/DiscoveryResult.java
    - src/main/java/org/specdriven/skill/discovery/SkillAutoDiscovery.java
  tests:
    - src/test/java/org/specdriven/skill/discovery/SkillAutoDiscoveryTest.java
---

# skill-auto-discovery.md - delta for hot-load-permission-guard

## MODIFIED Requirements

### Requirement: SkillAutoDiscovery

- When `SkillAutoDiscovery` invokes a configured hot-loader for a discovered skill, it MUST supply caller permission context for the hot-load operation
- The discovery hot-load permission context MUST identify discovery as the operation source so existing permission policies can allow or deny it deterministically
- If hot-loading a discovered skill is rejected by the permission guard, discovery MUST report that rejection as a per-skill hot-load failure
- A hot-load permission failure MUST NOT increment `failedCount` unless SQL registration itself also fails for that skill
- A hot-load permission failure MUST NOT prevent SQL registration from being attempted for the same skill
- A hot-load permission failure MUST NOT prevent remaining skills from being processed

## ADDED Scenarios

#### Scenario: authorized discovery hot-load proceeds before SQL registration

- GIVEN a discovered skill directory contains `SKILL.md` and the expected executor Java source file
- AND `SkillAutoDiscovery` is constructed with an enabled hot-loader whose permission provider returns `ALLOW`
- WHEN `discoverAndRegister()` is called
- THEN the hot-loader MUST be invoked with caller permission context before SQL registration
- AND `hotLoadedCount` MUST increase when the hot-load succeeds
- AND SQL registration MUST still proceed normally

#### Scenario: denied discovery hot-load is isolated from SQL registration

- GIVEN a discovered skill directory contains `SKILL.md` and the expected executor Java source file
- AND `SkillAutoDiscovery` is constructed with an enabled hot-loader whose permission provider returns `DENY`
- WHEN `discoverAndRegister()` is called
- THEN `hotLoadFailedCount` MUST increase
- AND `errors` MUST include a `SkillDiscoveryError` describing the permission failure
- AND SQL registration MUST still be attempted
- AND `failedCount` MUST remain unchanged when SQL registration succeeds

#### Scenario: confirmation-required discovery hot-load is reported as hot-load failure

- GIVEN a discovered skill directory contains matching Java source
- AND the configured enabled hot-loader returns a confirmation-required permission failure
- WHEN `discoverAndRegister()` is called
- THEN `hotLoadFailedCount` MUST increase
- AND `errors` MUST include a `SkillDiscoveryError` indicating that explicit confirmation is required
- AND remaining skills MUST still be processed
