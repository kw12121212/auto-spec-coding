---
mapping:
  implementation:
    - src/main/java/org/specdriven/skill/discovery/DiscoveryResult.java
    - src/main/java/org/specdriven/skill/discovery/SkillAutoDiscovery.java
  tests:
    - src/test/java/org/specdriven/skill/discovery/SkillAutoDiscoveryTest.java
---

# skill-auto-discovery.md - delta for trusted-source-activation-gate

## MODIFIED Requirements

### Requirement: SkillAutoDiscovery

- If hot-loading a discovered skill is rejected by the trusted-source activation gate, discovery MUST report that rejection as a per-skill hot-load failure
- A trusted-source hot-load failure MUST NOT increment `failedCount` unless SQL registration itself also fails for that skill
- A trusted-source hot-load failure MUST NOT prevent SQL registration from being attempted for the same skill
- A trusted-source hot-load failure MUST NOT prevent remaining skills from being processed
- Discovery MAY include the Java source path in the reported `SkillDiscoveryError` path for trust-gate rejection

## ADDED Scenarios

#### Scenario: trusted discovery hot-load proceeds before SQL registration

- GIVEN a discovered skill directory contains `SKILL.md` and the expected executor Java source file
- AND `SkillAutoDiscovery` is constructed with an enabled hot-loader whose permission provider returns `ALLOW`
- AND the configured trusted-source policy allows the discovered `(skillName, sourceHash)`
- WHEN `discoverAndRegister()` is called
- THEN the hot-loader MUST be invoked before SQL registration
- AND `hotLoadedCount` MUST increase when the hot-load succeeds
- AND SQL registration MUST still proceed normally

#### Scenario: untrusted discovery hot-load is isolated from SQL registration

- GIVEN a discovered skill directory contains `SKILL.md` and the expected executor Java source file
- AND `SkillAutoDiscovery` is constructed with an enabled hot-loader whose permission provider returns `ALLOW`
- AND the configured trusted-source policy rejects the discovered `(skillName, sourceHash)`
- WHEN `discoverAndRegister()` is called
- THEN `hotLoadFailedCount` MUST increase
- AND `errors` MUST include a `SkillDiscoveryError` describing the trusted-source failure
- AND SQL registration MUST still be attempted
- AND `failedCount` MUST remain unchanged when SQL registration succeeds

#### Scenario: untrusted discovery hot-load does not stop remaining skills

- GIVEN multiple discovered skill directories contain matching Java source
- AND hot-loading one discovered skill is rejected by the trusted-source activation gate
- WHEN `discoverAndRegister()` is called
- THEN remaining skills MUST still be processed
- AND SQL registration for remaining valid skills MUST still be attempted
