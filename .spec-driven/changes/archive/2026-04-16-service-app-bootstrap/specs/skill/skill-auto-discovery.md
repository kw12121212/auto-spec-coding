---
mapping:
  implementation:
    - src/main/java/org/specdriven/skill/discovery/SkillAutoDiscovery.java
    - src/main/java/org/specdriven/skill/sql/SkillSqlConverter.java
  tests:
    - src/test/java/org/specdriven/skill/discovery/SkillAutoDiscoveryTest.java
    - src/test/java/org/specdriven/skill/sql/SkillSqlConverterTest.java
---

## ADDED Requirements

### Requirement: SQL-centered declarative bootstrap baseline

The first M36 application bootstrap contract MUST treat SQL-centered service registration as the supported declarative baseline for bootstrapping service applications.

#### Scenario: services.sql is the supported first bootstrap entry
- GIVEN the first `service-app-bootstrap` change
- WHEN the repository documents or validates supported declarative application startup input
- THEN `services.sql` MUST be the supported declarative bootstrap entry for this change
- AND the change MUST NOT require a second equivalent declarative application entry format

#### Scenario: first bootstrap contract stays within repository-proven SQL scope
- GIVEN the repository's existing SQL-centered service registration behavior
- WHEN the first application bootstrap contract defines supported `services.sql` contents
- THEN it MUST stay within repository-proven idempotent SQL bootstrap statements
- AND it MUST NOT introduce a second non-SQL startup material or arbitrary runtime directive language in this change

#### Scenario: existing skill registration behavior remains a valid foundation
- GIVEN the repository's existing SQL-centered skill registration and discovery behavior
- WHEN the first application bootstrap contract is defined
- THEN that bootstrap contract MAY build on the same SQL-centered foundation
- AND it MUST NOT invalidate the existing skill discovery behavior for its current use cases
