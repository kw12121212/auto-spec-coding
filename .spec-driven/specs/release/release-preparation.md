---
mapping:
  implementation:
    - README.md
    - pom.xml
    - LEALONE_ALIGNMENT.md
    - scripts/install-lealone-upstream.sh
    - src/main/java/org/specdriven/skill/compiler/LealoneSkillSourceCompiler.java
    - src/main/java/org/specdriven/skill/executor/SkillServiceExecutorFactory.java
  tests:
    - src/test/java/org/specdriven/agent/event/LealoneAuditLogStoreTest.java
    - src/test/java/org/specdriven/sdk/ReleasePrepQuickstartExampleTest.java
    - src/test/java/org/specdriven/sdk/SdkBuilderEventTest.java
    - src/test/java/org/specdriven/sdk/SpecDrivenTest.java
    - src/test/java/org/specdriven/skill/compiler/SkillSourceCompilerTest.java
    - src/test/java/org/specdriven/skill/executor/SkillServiceExecutorFactoryTest.java
    - src/test/java/org/specdriven/agent/agent/LealoneSessionStoreTest.java
    - src/test/java/org/specdriven/agent/llm/LealoneRuntimeLlmConfigStoreTest.java
    - src/test/java/org/specdriven/agent/tool/cache/LealoneToolCacheTest.java
    - src/test/java/org/specdriven/agent/question/LealoneQuestionStoreTest.java
    - src/test/java/org/specdriven/agent/vault/LealoneVaultTest.java
    - src/test/java/org/specdriven/agent/registry/LealoneTaskStoreTest.java
    - src/test/java/org/specdriven/agent/registry/LealoneTeamStoreTest.java
    - src/test/java/org/specdriven/agent/registry/LealoneCronStoreTest.java
---

# Release Preparation Spec

## ADDED Requirements

### Requirement: Repository release overview

- The repository MUST provide a top-level release overview that describes the project as implemented, not as a pre-implementation roadmap
- The release overview MUST identify the three supported integration surfaces: Native Java SDK, JSON-RPC, and HTTP REST API
- The release overview MUST document the baseline build and test commands required to verify the project locally

#### Scenario: README reflects implemented project state
- GIVEN a developer opens the repository root documentation
- WHEN they read the top-level overview
- THEN they MUST see the project described as an implemented Java agent SDK with Native Java SDK, JSON-RPC, and HTTP REST API surfaces
- AND they MUST NOT be told that implementation has not yet started

### Requirement: Quickstart and interface examples

- The repository MUST provide a Java SDK quickstart showing the minimal flow to build the SDK, create an agent, and run a prompt
- The repository MUST provide a JSON-RPC usage example showing initialization and at least one agent execution request
- The repository MUST provide an HTTP usage example showing at least one authenticated agent execution request
- All documented examples MUST be consistent with currently supported operations and payload shapes from the existing runtime specs

#### Scenario: Java SDK quickstart is discoverable
- GIVEN a developer wants to use the library directly from Java
- WHEN they follow the repository quickstart
- THEN they MUST be able to find a minimal Java SDK example without consulting source code internals

#### Scenario: JSON-RPC and HTTP examples are discoverable
- GIVEN a developer wants to integrate over process or network boundaries
- WHEN they read the repository usage documentation
- THEN they MUST be able to find one JSON-RPC example and one HTTP example
- AND each example MUST use method names or route shapes that match the current published specs

### Requirement: Repo-local Maven release metadata

- The Maven project descriptor MUST include release-facing metadata sufficient for repo-local package inspection and consumption
- The committed release metadata MUST include project identity, license, and source-control reference information
- Repo-local release preparation MUST NOT require live publishing credentials or an actual publish step

#### Scenario: Local package metadata is inspectable
- GIVEN a developer inspects the committed Maven project descriptor
- WHEN they review the release-facing metadata
- THEN they MUST find project identity and license information
- AND they MUST find source-control reference information
- AND they MUST NOT need external publishing credentials to validate the repository state

### Requirement: Stable repo-local Maven test verification

- The repository MUST provide a committed Maven test configuration that allows the default repo-local `mvn test` workflow to complete without cross-test interference from shared embedded runtime state.

#### Scenario: full Maven test workflow completes under committed defaults
- GIVEN a developer runs the repository's default Maven test workflow from a clean checkout
- WHEN the tests execute under the committed build configuration
- THEN the workflow MUST complete without failures caused by shared embedded runtime file locking between unrelated tests
- AND the developer MUST NOT need to hand-edit local test parallelism settings first

### Requirement: Verified Lealone upstream baseline

- The repository MUST identify the latest Lealone upstream source baseline that the current codebase has been verified against.
- The identified baseline MUST be inspectable from committed repository materials without requiring unpublished local environment state.
- The verified baseline MUST correspond to the Lealone artifacts used by the project's current build.

#### Scenario: Developer inspects Lealone baseline

- GIVEN a developer inspects the repository after the alignment change
- WHEN they review the committed release or build materials
- THEN they MUST be able to determine which latest Lealone upstream baseline was verified
- AND they MUST NOT need to infer compatibility from an ambiguous snapshot label alone

### Requirement: Latest-baseline compatibility verification

- The repository MUST provide a repo-local verification workflow that checks compatibility-sensitive Lealone integration points against the verified upstream baseline.
- The verification workflow MUST cover at least compilation and focused tests for compiler integration, service-executor SPI integration, and embedded JDBC-backed store integrations.
- The verification workflow MUST fail when a covered compatibility regression is present.

#### Scenario: Compatibility verification catches drift

- GIVEN the repository has been aligned to a verified Lealone upstream baseline
- WHEN a developer runs the documented compatibility verification workflow after an incompatible change
- THEN the workflow MUST report failure
- AND the failure MUST surface before a release consumer relies on the broken baseline

### Requirement: Lealone alignment notes

- The repository MUST provide a concise alignment note summarizing which direct Lealone integration areas were checked during the latest refresh and whether each area remained compatible or required adaptation.
- The alignment note MUST remain limited to project-relevant integration areas and direct follow-up recommendations.
- The alignment note MAY mention additional upstream Lealone capabilities that could benefit the project later, but it MUST distinguish those recommendations from the currently implemented alignment.

#### Scenario: Developer reviews alignment notes

- GIVEN a developer wants to understand the result of the latest Lealone refresh
- WHEN they review the repository's alignment note
- THEN they MUST see which integration areas were checked
- AND they MUST be able to distinguish implemented compatibility work from future recommendations
