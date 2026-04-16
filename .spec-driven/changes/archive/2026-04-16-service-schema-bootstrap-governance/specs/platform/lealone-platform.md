---
mapping:
  implementation:
    - src/main/java/org/specdriven/sdk/LealonePlatform.java
    - src/main/java/org/specdriven/sdk/SpecDriven.java
  tests:
    - src/test/java/org/specdriven/sdk/LealonePlatformTest.java
    - src/test/java/org/specdriven/sdk/SpecDrivenTest.java
---

## ADDED Requirements

### Requirement: Bootstrap input is preflight validated
The system MUST validate the complete supported `services.sql` bootstrap input before executing any bootstrap-managed statement against the assembled platform runtime.

#### Scenario: mixed bootstrap file is rejected atomically
- GIVEN a readable `services.sql` file whose first statement is supported
- AND a later statement in the same file is unsupported or non-idempotent
- WHEN bootstrap validation runs
- THEN bootstrap MUST fail explicitly
- AND it MUST NOT execute the earlier supported statement
- AND it MUST NOT report any bootstrap-managed object from that file as applied

### Requirement: Bootstrap governance boundary
The system MUST limit automatic bootstrap execution to the governed startup statement set and reject declarative runtime directives or mutating SQL from the automatic bootstrap path.

#### Scenario: governed bootstrap statements are accepted
- GIVEN a readable `services.sql` file that contains only `CREATE TABLE IF NOT EXISTS` and `CREATE SERVICE IF NOT EXISTS` statements
- WHEN bootstrap runs through the supported platform-backed entry path
- THEN bootstrap MAY apply those statements
- AND repeated bootstrap with the same file MUST remain idempotent

#### Scenario: non-idempotent table declaration is rejected
- GIVEN a readable `services.sql` file that contains `CREATE TABLE` without `IF NOT EXISTS`
- WHEN bootstrap validation runs
- THEN bootstrap MUST fail explicitly
- AND it MUST identify the unsupported statement as outside the automatic bootstrap contract

#### Scenario: runtime directive inside services.sql is rejected
- GIVEN a readable `services.sql` file that attempts to set runtime configuration or other startup directives in addition to bootstrap-managed SQL
- WHEN bootstrap validation runs
- THEN bootstrap MUST fail explicitly
- AND it MUST treat the directive as unsupported startup input
