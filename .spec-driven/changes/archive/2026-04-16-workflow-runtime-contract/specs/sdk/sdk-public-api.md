---
mapping:
  implementation:
    - src/main/java/org/specdriven/sdk/SpecDriven.java
    - src/main/java/org/specdriven/sdk/SdkBuilder.java
    - src/main/java/org/specdriven/sdk/LealonePlatform.java
    - src/main/java/org/specdriven/sdk/WorkflowStatus.java
    - src/main/java/org/specdriven/sdk/WorkflowInstanceView.java
    - src/main/java/org/specdriven/sdk/WorkflowResultView.java
  tests:
    - src/test/java/org/specdriven/sdk/SpecDrivenTest.java
    - src/test/java/org/specdriven/sdk/WorkflowRuntimeTest.java
    - src/test/java/org/specdriven/sdk/SdkBuilderTest.java
    - src/test/java/org/specdriven/sdk/SpecDrivenPlatformTest.java
---

## ADDED Requirements

### Requirement: SDK workflow declaration registration

The SDK public surface MUST support registering a declared workflow through both the in-process domain contract and the first supported workflow SQL declaration path.

#### Scenario: Domain-declared workflow becomes startable
- GIVEN application code registers a supported workflow declaration through the SDK public surface
- WHEN registration succeeds
- THEN the workflow MUST become startable by its declared name through the SDK workflow runtime surface

#### Scenario: SQL-declared workflow becomes startable
- GIVEN application code registers a supported workflow through the supported SQL declaration path exposed by the SDK or platform surface
- WHEN registration succeeds
- THEN the workflow MUST become startable by the same declared name through the SDK workflow runtime surface

#### Scenario: Unsupported workflow declaration is rejected
- GIVEN application code attempts to register unsupported or malformed workflow declaration input
- WHEN registration is requested through either supported declaration path
- THEN registration MUST fail explicitly
- AND the workflow MUST NOT become startable through the SDK workflow runtime surface

### Requirement: SDK workflow instance operations

The SDK public surface MUST expose workflow runtime operations for starting a declared workflow, querying workflow instance state, and reading the current workflow result view.

#### Scenario: Start declared workflow
- GIVEN a declared workflow named `invoice-approval`
- WHEN application code starts that workflow with supported structured input
- THEN the SDK MUST return a workflow instance view with a non-blank `workflowId`
- AND the returned view MUST identify `invoice-approval`
- AND the returned status MUST be `ACCEPTED`

#### Scenario: Query workflow state by ID
- GIVEN a previously started workflow instance
- WHEN application code queries its state through the SDK workflow runtime surface
- THEN the SDK MUST return a state view for the same `workflowId`
- AND the view MUST expose the current workflow status

#### Scenario: Read workflow result before completion
- GIVEN a previously started workflow instance that is not in a terminal status
- WHEN application code reads its current result view
- THEN the SDK MUST return the current workflow status
- AND the result payload MUST be null or empty

#### Scenario: Unknown workflow name is rejected
- GIVEN no declared workflow named `invoice-approval`
- WHEN application code attempts to start `invoice-approval`
- THEN the SDK MUST fail explicitly
- AND it MUST NOT report a started workflow instance

#### Scenario: Unknown workflow instance is rejected
- GIVEN no workflow instance exists for the requested `workflowId`
- WHEN application code queries workflow state or result
- THEN the SDK MUST fail explicitly

### Requirement: SDK workflow compatibility boundary

Introducing the SDK workflow runtime surface MUST NOT change existing agent-oriented SDK behavior unless callers explicitly use workflow features.

#### Scenario: Existing agent creation remains compatible
- GIVEN application code already uses `SpecDriven.builder().build()` and `createAgent()`
- WHEN the SDK workflow runtime surface is available
- THEN the existing agent-oriented SDK flow MUST remain available
- AND its observable behavior MUST remain unchanged unless the caller explicitly uses workflow features
