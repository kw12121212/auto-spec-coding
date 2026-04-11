---
mapping:
  implementation:
    - src/main/java/org/specdriven/skill/discovery/DiscoveryResult.java
    - src/main/java/org/specdriven/skill/discovery/SkillAutoDiscovery.java
    - src/main/java/org/specdriven/skill/discovery/SkillDiscoveryError.java
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

- MUST be a Java record with fields: `registeredCount` (int), `failedCount` (int), `errors` (List<SkillDiscoveryError>)
- `errors` MUST be returned as an unmodifiable list
- MUST be in the `org.specdriven.skill.discovery` package

### Requirement: SkillAutoDiscovery

- MUST accept a JDBC URL (String) and a skills directory (Path) at construction
- MUST provide a `discoverAndRegister()` method that returns `DiscoveryResult`
- MUST scan only direct subdirectories of skillsDir (non-recursive)
- MUST process only subdirectories that contain a `SKILL.md` file
- MUST parse each SKILL.md using `SkillMarkdownParser` and generate DDL using `SkillSqlConverter.convert(frontmatter, skillDir)`
- MUST execute each generated CREATE SERVICE SQL via JDBC `executeUpdate` using the provided JDBC URL
- MUST continue processing remaining skills when one skill fails (partial success)
- MUST collect per-skill `SkillSqlException` and `SQLException` failures into `DiscoveryResult.errors`
- MUST throw `SkillSqlException` when the skillsDir itself cannot be listed (directory-level failure)
- MUST return `DiscoveryResult` with correct `registeredCount` and `failedCount` totals
- MUST be in the `org.specdriven.skill.discovery` package
