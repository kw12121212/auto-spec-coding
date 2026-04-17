---
mapping:
  implementation:
    - README.md
    - pom.xml
    - src/main/java/org/specdriven/sdk/LealonePlatform.java
  tests:
    - src/test/java/org/specdriven/sdk/LealonePlatformTest.java
---

## ADDED Requirements

### Requirement: Standalone packaged runtime jar

The repository build MUST produce a single packaged runtime jar for the supported Java runtime entrypoint that operators can start with `java -jar` without adding an external dependency directory or repository checkout files to the runtime classpath.

#### Scenario: packaged runtime is self-contained
- GIVEN an operator builds the supported packaged runtime artifact
- WHEN they start the Java runtime entrypoint from that artifact with `java -jar`
- THEN the runtime MUST have access to its required Java dependencies from that single jar
- AND the operator MUST NOT need a sibling dependency directory on disk

#### Scenario: packaged runtime keeps bundled runtime assets available
- GIVEN an operator starts the packaged runtime jar outside the source repository checkout
- WHEN the runtime needs a supported bundled default runtime asset
- THEN the packaged runtime MUST still make that asset available without requiring repository-relative files to exist

## MODIFIED Requirements

### Requirement: Runtime startup documentation
Previously: The repository MUST document the supported development and packaged-runtime startup commands for service applications.

The repository MUST document the supported development and standalone packaged-runtime startup commands for service applications.

#### Scenario: operator finds packaged startup command
- GIVEN an operator has a built runtime artifact
- WHEN they read the repository runtime documentation
- THEN they MUST find the supported packaged-runtime `java -jar` startup command
- AND the command MUST identify the required startup input
- AND the command MUST NOT require a separate dependency directory on disk
