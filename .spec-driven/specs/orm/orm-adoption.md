---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/question/DeliveryLogModel.java
    - src/main/java/org/specdriven/agent/question/QuestionModel.java
    - src/main/java/org/specdriven/agent/question/LealoneDeliveryLogStore.java
    - src/main/java/org/specdriven/agent/question/LealoneQuestionStore.java
  tests:
    - src/test/java/org/specdriven/agent/question/OrmAdoptionGuidelinesTest.java
---

### Requirement: orm-admission-criteria
The system MUST migrate a Lealone Store to ORM only when all of the following
conditions are met:
- The Store maps to a single table with no JOIN-based queries
- The Store's full CRUD surface can be expressed using the Lealone ORM Model
  API without raw SQL workarounds
- The migration reduces observable boilerplate without changing the Store's
  public interface or return types
- Behavioral parity is verifiable through the Store's public API and direct
  SQL table checks

#### Scenario: candidate qualifies
- GIVEN a Store owns a single table, has simple CRUD methods, and no complex
  query requirements
- WHEN evaluated against the admission criteria
- THEN the Store MAY be migrated to ORM in a subsequent change

#### Scenario: candidate does not qualify
- GIVEN a Store requires JOINs, raw SQL for performance, or has a complex
  lifecycle that cannot be expressed via the ORM Model API
- WHEN evaluated against the admission criteria
- THEN the Store MUST remain as raw JDBC and MUST NOT be migrated

### Requirement: orm-interface-preservation
A Store migrated to ORM MUST preserve its public method signatures, parameter
types, and return types exactly as they were before migration.

#### Scenario: migrated store interface unchanged
- GIVEN a Store has been migrated to ORM
- WHEN callers invoke any public Store method with the same arguments as before
- THEN the method behaves identically and returns the same types as before migration

### Requirement: orm-escape-hatch
The escape hatch for ORM adoption is the decision not to migrate. A Store that
does not meet the admission criteria MUST remain as raw JDBC.

#### Scenario: non-migrated store operates alongside ORM stores
- GIVEN one or more Stores are backed by raw JDBC and one or more Stores are
  backed by ORM, all using the same embedded Lealone JDBC URL
- WHEN all Stores are initialized and used concurrently
- THEN each Store MUST read and write its own data correctly through its public
  API, independent of whether other Stores use ORM or raw JDBC

### Requirement: orm-coexistence
ORM-backed Stores and raw-JDBC-backed Stores MUST coexist on the same embedded
Lealone database instance without table conflicts, data loss, or behavioral
regressions in any participating Store's public contract.

#### Scenario: interleaved writes across ORM and raw JDBC stores
- GIVEN `LealoneDeliveryLogStore` (ORM), `LealoneQuestionStore` (ORM), and
  `LealonePolicyStore` (raw JDBC) are all initialized with the same JDBC URL
- WHEN each Store writes at least one record through its public API
- THEN each Store MUST return its own written record correctly through its own
  public read methods, without interference from the other Stores
