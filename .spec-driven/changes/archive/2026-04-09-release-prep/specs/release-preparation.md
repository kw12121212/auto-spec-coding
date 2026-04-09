# Release Preparation Spec (Delta: release-prep)

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
