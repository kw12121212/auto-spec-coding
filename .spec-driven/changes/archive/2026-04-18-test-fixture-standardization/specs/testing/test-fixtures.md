---
mapping:
  implementation:
    - src/test/java/org/specdriven/agent/testsupport/CapturingEventBus.java
    - src/test/java/org/specdriven/agent/testsupport/LealoneTestDb.java
  tests:
    - src/test/java/org/specdriven/agent/registry/LealoneTaskStoreTest.java
    - src/test/java/org/specdriven/agent/registry/LealoneTeamStoreTest.java
    - src/test/java/org/specdriven/agent/registry/LealoneCronStoreTest.java
    - src/test/java/org/specdriven/agent/vault/LealoneVaultTest.java
    - src/test/java/org/specdriven/agent/vault/VaultFactoryTest.java
    - src/test/java/org/specdriven/agent/loop/LealoneLoopIterationStoreTest.java
    - src/test/java/org/specdriven/agent/interactive/InteractiveCommandHandlerTest.java
    - src/test/java/org/specdriven/agent/question/RetryingDeliveryChannelTest.java
    - src/test/java/org/specdriven/agent/llm/LealoneLlmCacheTest.java
---

## ADDED Requirements

### Requirement: shared-capturing-event-bus
The test support package MUST provide a shared `CapturingEventBus` implementation that any test class can import and use without defining a private copy.

#### Scenario: capture published events
- GIVEN a test instantiates `CapturingEventBus`
- WHEN events are published via `publish(Event)`
- THEN `getEvents()` returns all published events in order

#### Scenario: clear captured events
- GIVEN a `CapturingEventBus` that has received events
- WHEN `clear()` is called
- THEN `getEvents()` returns an empty list

### Requirement: lealone-test-db-helper
The test support package MUST provide a `LealoneTestDb.freshJdbcUrl()` static method that returns a unique in-memory Lealone JDBC URL suitable for test isolation.

#### Scenario: unique URL per call
- GIVEN two consecutive calls to `LealoneTestDb.freshJdbcUrl()`
- WHEN both URLs are used to create Lealone stores in the same test run
- THEN the two stores operate on independent in-memory databases with no shared state
