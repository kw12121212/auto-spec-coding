---
mapping:
  implementation:
    - src/main/java/org/specdriven/skill/hotload/SkillHotLoaderException.java
    - src/main/java/org/specdriven/skill/hotload/SkillHotLoadPermissionException.java
    - src/main/java/org/specdriven/skill/hotload/SkillHotLoadTrustException.java
    - src/main/java/org/specdriven/skill/hotload/SkillLoadResult.java
    - src/main/java/org/specdriven/skill/hotload/SkillHotLoader.java
    - src/main/java/org/specdriven/skill/hotload/SkillSourceTrustPolicy.java
    - src/main/java/org/specdriven/skill/hotload/LealoneSkillHotLoader.java
  tests:
    - src/test/java/org/specdriven/skill/hotload/SkillHotLoaderTest.java
    - src/test/java/org/specdriven/skill/executor/SkillServiceExecutorFactoryTest.java
---

# skill-hot-loader.md - delta for trusted-source-activation-gate

## ADDED Requirements

### Requirement: Skill hot-load trusted-source policy

- The hot-loader MUST support a local programmatic trusted-source policy for activation operations
- The trusted-source policy MUST decide trust using `skillName` and `sourceHash`
- The trusted-source policy MUST NOT require raw Java source text to make a trust decision
- The trusted-source policy MAY use additional non-secret metadata when available, but `skillName` and `sourceHash` MUST be sufficient to identify the source being activated
- The default behavior for enabled activation without a configured trusted-source policy MUST fail closed
- A configured trusted-source policy that rejects `(skillName, sourceHash)` MUST prevent that source from being activated

### Requirement: Skill hot-load trusted-source failure signal

- Trusted-source rejection MUST expose a visible failure to callers
- Trusted-source rejection MUST be distinguishable from `SkillHotLoadPermissionException`
- Trusted-source rejection MUST be distinguishable from `SkillHotLoaderException` failures that represent compiler, cache, registry, or ClassLoader infrastructure problems
- Trusted-source rejection failures MUST include the skill name and source hash in the failure message

### Requirement: Skill hot-load activation trust gate

- Enabled hot-load `load` and `replace` operations MUST check source trust before compilation, class-cache reads, class-cache writes, active registry mutation, or failed-skill registry mutation
- The trusted-source check MUST occur after the existing default-disabled activation gate
- The trusted-source check MUST occur after the existing permission check for permission-aware `load` and `replace`
- When hot-loading activation is disabled, existing disabled behavior MUST remain unchanged and MUST still avoid permission, trust, compile, cache, and registry side effects
- When permission is denied or confirmation is required, existing permission failure behavior MUST remain unchanged and MUST still avoid trust, compile, cache, and registry side effects
- When source trust allows the requested `(skillName, sourceHash)`, existing authorized enabled `load` and `replace` behavior MUST proceed unchanged
- `unload(skillName)` MUST NOT require a trusted-source check because it does not introduce new Java source into the activation path

## ADDED Scenarios

#### Scenario: trusted load preserves enabled behavior

- GIVEN hot-loading activation is enabled
- AND the permission provider returns `ALLOW` for `skill.hotload.load` on `skill:<skillName>`
- AND the trusted-source policy allows `(skillName, sourceHash)`
- WHEN `load(skillName, entryClassName, javaSource, sourceHash)` is called with valid source
- THEN `SkillLoadResult.success` MUST be `true`
- AND `activeLoader(skillName)` MUST return a non-empty `Optional`

#### Scenario: untrusted load has no side effects

- GIVEN hot-loading activation is enabled
- AND the permission provider returns `ALLOW` for `skill.hotload.load` on `skill:<skillName>`
- AND the trusted-source policy rejects `(skillName, sourceHash)`
- WHEN `load(skillName, entryClassName, javaSource, sourceHash)` is called
- THEN the operation MUST expose a trusted-source failure
- AND the compiler MUST NOT be invoked
- AND the class cache MUST NOT be read or populated
- AND `activeLoader(skillName)` MUST return `Optional.empty()`
- AND `failedSkillNames()` MUST NOT contain `skillName`

#### Scenario: untrusted replace preserves active loader

- GIVEN hot-loading activation is enabled
- AND `skillName` is already registered with a working loader
- AND the permission provider returns `ALLOW` for `skill.hotload.replace` on `skill:<skillName>`
- AND the trusted-source policy rejects the replacement `(skillName, sourceHash)`
- WHEN `replace(skillName, entryClassName, javaSource, sourceHash)` is called
- THEN the operation MUST expose a trusted-source failure
- AND the existing active loader MUST remain unchanged
- AND the compiler MUST NOT be invoked
- AND the class cache MUST NOT be read or populated
- AND `failedSkillNames()` MUST NOT contain `skillName`

#### Scenario: missing trusted-source policy fails closed

- GIVEN hot-loading activation is enabled
- AND the permission provider returns `ALLOW` for the requested `load` or `replace`
- AND no trusted-source policy is configured
- WHEN `load` or `replace` is called
- THEN the operation MUST expose a trusted-source failure
- AND no compile/cache/registry side effect may occur

#### Scenario: permission failure occurs before trusted-source check

- GIVEN hot-loading activation is enabled
- AND the permission provider returns `DENY` or `CONFIRM` for the requested `load` or `replace`
- WHEN the operation is called
- THEN the operation MUST expose the existing permission failure
- AND the trusted-source policy MUST NOT be consulted
- AND no compile/cache/registry side effect may occur

#### Scenario: disabled loader behavior remains unchanged

- GIVEN a newly constructed `LealoneSkillHotLoader`
- WHEN a `load` or `replace` operation is attempted before explicit enablement
- THEN existing default-disabled behavior MUST be preserved
- AND no permission provider decision or trusted-source decision is required to avoid compile/cache/registry side effects
