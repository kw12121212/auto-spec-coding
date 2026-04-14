---
mapping:
  implementation:
    - src/main/java/org/specdriven/skill/sql/SkillMarkdownParser.java
  tests:
    - src/test/java/org/specdriven/skill/sql/SkillMarkdownParserTest.java
    - src/test/java/org/specdriven/skill/sql/RealSkillsIntegrationTest.java
    - src/test/java/org/specdriven/skill/discovery/SkillAutoDiscoveryTest.java
    - src/test/java/org/specdriven/skill/discovery/RealSkillsDiscoveryTest.java
---

## MODIFIED Requirements

### Requirement: SkillMarkdownParser
Previously: MUST throw `SkillSqlException` if required fields `skill_id` or `name` are missing from the frontmatter.
MUST accept `skill_id`, `author`, `type`, and `version` either as top-level frontmatter fields or as fields under a top-level `metadata` object, with top-level fields taking precedence when both forms are present.
MUST throw `SkillSqlException` if required field `skill_id` is missing from both the top-level frontmatter and `metadata`.
MUST continue to throw `SkillSqlException` if required field `name` is missing from the top-level frontmatter.

#### Scenario: Metadata skill id is accepted
- GIVEN a SKILL.md file whose frontmatter contains top-level `name` and nested `metadata.skill_id`
- WHEN the skill file is parsed
- THEN the parser MUST return a `SkillFrontmatter` with that skill id

#### Scenario: Existing top-level skill id remains accepted
- GIVEN a SKILL.md file whose frontmatter contains top-level `skill_id` and `name`
- WHEN the skill file is parsed
- THEN the parser MUST return a `SkillFrontmatter` with that skill id
