---
mapping:
  implementation:
    - pom.xml
    - README.md
    - LEALONE_ALIGNMENT.md
    - scripts/install-lealone-upstream.sh
    - src/main/java/org/specdriven/skill/compiler/LealoneSkillSourceCompiler.java
    - src/main/java/org/specdriven/skill/executor/SkillServiceExecutorFactory.java
  tests:
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

# Release Preparation Spec (Delta: latest-lealone-alignment)

## ADDED Requirements

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

## UNCHANGED Requirements

- Existing Native Java SDK, JSON-RPC, HTTP REST API, and current Lealone-backed domain behaviors remain in effect without semantic expansion from this delta.
