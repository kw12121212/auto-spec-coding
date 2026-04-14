---
mapping:
  implementation:
    - src/main/java/org/specdriven/skill/discovery/DiscoveryResult.java
    - src/main/java/org/specdriven/skill/discovery/SkillAutoDiscovery.java
    - src/main/java/org/specdriven/skill/discovery/SkillDiscoveryError.java
    - src/main/java/org/specdriven/skill/sql/SkillSqlConverter.java
  tests:
    - src/test/java/org/specdriven/skill/discovery/RealSkillsDiscoveryTest.java
    - src/test/java/org/specdriven/skill/discovery/SkillAutoDiscoveryTest.java
---

# skill-auto-discovery.md

## Requirements

### Requirement: SkillDiscoveryError record

- MUST be a Java record with fields: `path` (Path) and `errorMessage` (String)
- MUST be in the `org.specdriven.skill.discovery` package

### Requirement: DiscoveryResult record

- MUST be a Java record with fields: `registeredCount` (int), `failedCount` (int),
  `hotLoadedCount` (int), `hotLoadFailedCount` (int), and `errors`
  (List<SkillDiscoveryError>)
- `registeredCount` MUST count SQL registration successes only
- `failedCount` MUST count SQL registration failures only
- `hotLoadedCount` MUST count successful hot-loads attempted during discovery
- `hotLoadFailedCount` MUST count failed hot-loads attempted during discovery
- `errors` MUST be returned as an unmodifiable list
- `errors` MAY include either SQL registration failures or hot-load failures using
  `SkillDiscoveryError`
- MUST be in the `org.specdriven.skill.discovery` package

### Requirement: SkillAutoDiscovery

- MUST accept a JDBC URL (String) and a skills directory (Path) at construction
- MUST support construction with an optional `SkillHotLoader`
- MUST provide a `discoverAndRegister()` method that returns `DiscoveryResult`
- MUST scan only direct subdirectories of skillsDir (non-recursive)
- MUST process only subdirectories that contain a `SKILL.md` file
- MUST parse each SKILL.md using `SkillMarkdownParser` and generate DDL using `SkillSqlConverter.convert(frontmatter, skillDir)`
- When a `SkillHotLoader` is configured and a discovered skill directory contains the
  expected executor Java source file alongside `SKILL.md`, discovery MUST read that
  source, compute a deterministic source hash, and call
  `SkillHotLoader.load(skillName, entryClassName, javaSource, sourceHash)` with caller
  permission context before SQL registration
- The discovery hot-load permission context MUST identify discovery as the operation
  source so existing permission policies can allow or deny it deterministically
- If the expected executor Java source file is absent, discovery MUST skip hot-loading
  and continue with SQL registration
- MUST execute each generated CREATE SERVICE SQL via JDBC `executeUpdate` using the provided JDBC URL
- MUST continue processing remaining skills when one skill fails (partial success)
- MUST collect per-skill `SkillSqlException` and `SQLException` failures into `DiscoveryResult.errors`
- MUST append per-skill hot-load failures to `DiscoveryResult.errors`
- If hot-loading a discovered skill is rejected by the permission guard, discovery
  MUST report that rejection as a per-skill hot-load failure
- If hot-loading a discovered skill is rejected by the trusted-source activation
  gate, discovery MUST report that rejection as a per-skill hot-load failure
- When a `SkillHotLoader` is configured but hot-loading activation is disabled, discovery MUST continue processing skills instead of treating disabled activation as a directory-level failure
- When a `SkillHotLoader` is configured but hot-loading activation is disabled, discovery MUST leave SQL registration behavior unchanged
- A hot-load permission failure MUST NOT increment `failedCount` unless SQL
  registration itself also fails for that skill
- A hot-load permission failure MUST NOT prevent SQL registration from being
  attempted for the same skill
- A hot-load permission failure MUST NOT prevent remaining skills from being
  processed
- A trusted-source hot-load failure MUST NOT increment `failedCount` unless SQL
  registration itself also fails for that skill
- A trusted-source hot-load failure MUST NOT prevent SQL registration from being
  attempted for the same skill
- A trusted-source hot-load failure MUST NOT prevent remaining skills from being
  processed
- A hot-load failure MUST NOT increment `failedCount` unless SQL registration itself
  also fails for that skill
- MUST throw `SkillSqlException` when the skillsDir itself cannot be listed (directory-level failure)
- MUST return `DiscoveryResult` with correct SQL and hot-load totals
- MUST be in the `org.specdriven.skill.discovery` package

#### Scenario: discovery without hot-loader preserves current behavior

- GIVEN `SkillAutoDiscovery` is constructed without a `SkillHotLoader`
- WHEN `discoverAndRegister()` is called
- THEN SQL registration behavior MUST match the pre-integration implementation
- AND `hotLoadedCount` and `hotLoadFailedCount` MUST both be `0`

#### Scenario: skill with matching Java source is hot-loaded before registration

- GIVEN a discovered skill directory contains `SKILL.md` and the expected executor
  Java source file
- WHEN `discoverAndRegister()` is called with a configured `SkillHotLoader`
- THEN the loader MUST be invoked for that skill with caller permission context before SQL registration
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

#### Scenario: untrusted discovery hot-load is isolated from SQL registration

- GIVEN a discovered skill directory contains `SKILL.md` and the expected executor
  Java source file
- AND `SkillAutoDiscovery` is constructed with an enabled hot-loader whose
  permission provider returns `ALLOW`
- AND the configured trusted-source policy rejects the discovered
  `(skillName, sourceHash)`
- WHEN `discoverAndRegister()` is called
- THEN `hotLoadFailedCount` MUST increase
- AND `errors` MUST include a `SkillDiscoveryError` describing the
  trusted-source failure
- AND SQL registration MUST still be attempted
- AND `failedCount` MUST remain unchanged when SQL registration succeeds

#### Scenario: untrusted discovery hot-load does not stop remaining skills

- GIVEN multiple discovered skill directories contain matching Java source
- AND hot-loading one discovered skill is rejected by the trusted-source
  activation gate
- WHEN `discoverAndRegister()` is called
- THEN remaining skills MUST still be processed
- AND SQL registration for remaining valid skills MUST still be attempted
