---
mapping:
  implementation:
    - pom.xml
  tests:
    - src/test/java/org/specdriven/sdk/SpecDrivenTest.java
    - src/test/java/org/specdriven/sdk/SdkBuilderEventTest.java
    - src/test/java/org/specdriven/agent/event/LealoneAuditLogStoreTest.java
---

## ADDED Requirements

### Requirement: Stable repo-local Maven test verification

The repository MUST provide a committed Maven test configuration that allows the default repo-local `mvn test` workflow to complete without cross-test interference from shared embedded runtime state.

#### Scenario: full Maven test workflow completes under committed defaults
- GIVEN a developer runs the repository's default Maven test workflow from a clean checkout
- WHEN the tests execute under the committed build configuration
- THEN the workflow MUST complete without failures caused by shared embedded runtime file locking between unrelated tests
- AND the developer MUST NOT need to hand-edit local test parallelism settings first
