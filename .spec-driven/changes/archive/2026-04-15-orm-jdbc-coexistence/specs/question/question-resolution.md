---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/permission/LealonePolicyStore.java
    - src/main/java/org/specdriven/agent/question/LealoneDeliveryLogStore.java
    - src/main/java/org/specdriven/agent/question/LealoneQuestionStore.java
  tests:
    - src/test/java/org/specdriven/agent/question/OrmJdbcCoexistenceTest.java
---

# Question Resolution - Delta Spec: orm-jdbc-coexistence

## ADDED Requirements

### Requirement: ORM and raw JDBC Store coexistence

The system MUST allow ORM-backed question-domain Stores and raw JDBC-backed
Stores to operate against the same Lealone embedded database without changing
their public Store contracts.

#### Scenario: Shared database initialization

- GIVEN a fresh Lealone embedded database URL
- WHEN `LealoneDeliveryLogStore`, `LealoneQuestionStore`, and a raw JDBC-backed
  Store are constructed with that same JDBC URL
- THEN each Store MUST initialize its required tables without removing or
  corrupting the tables owned by the other Stores

#### Scenario: Interleaved Store operations remain independently readable

- GIVEN ORM-backed Stores and a raw JDBC-backed Store using the same Lealone
  embedded database URL
- WHEN callers save a question, save a delivery attempt, and persist a raw JDBC
  Store record in an interleaved order
- THEN each saved value MUST be readable through the public API of the Store
  that owns it

#### Scenario: ORM table interoperability survives raw JDBC Store usage

- GIVEN ORM-backed question-domain Stores and a raw JDBC-backed Store using the
  same Lealone embedded database URL
- WHEN callers save question and delivery-log records through the ORM-backed
  Stores after the raw JDBC Store has initialized or written its own records
- THEN those question and delivery-log records MUST remain visible through the
  existing `questions` and `delivery_log` table columns

#### Scenario: Raw JDBC Store remains outside ORM migration

- GIVEN coexistence has been verified
- THEN the raw JDBC-backed Store MUST NOT require ORM model mappings, new public
  methods, or a new construction contract in order to coexist with the
  ORM-backed Stores
