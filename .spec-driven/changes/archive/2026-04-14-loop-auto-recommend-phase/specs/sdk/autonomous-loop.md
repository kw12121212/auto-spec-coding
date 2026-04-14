---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/loop/DefaultLoopDriver.java
    - src/main/java/org/specdriven/agent/loop/LoopCandidate.java
    - src/main/java/org/specdriven/agent/loop/LoopContext.java
    - src/main/java/org/specdriven/agent/loop/LoopPipeline.java
    - src/main/java/org/specdriven/agent/loop/LoopScheduler.java
    - src/main/java/org/specdriven/agent/loop/PipelinePhase.java
    - src/main/java/org/specdriven/agent/loop/SequentialMilestoneScheduler.java
    - src/main/java/org/specdriven/agent/loop/SpecDrivenPipeline.java
    - src/main/resources/loop-phases/recommend.txt
  tests:
    - src/test/java/org/specdriven/agent/loop/DefaultLoopDriverTest.java
    - src/test/java/org/specdriven/agent/loop/PipelinePhaseTest.java
    - src/test/java/org/specdriven/agent/loop/SequentialMilestoneSchedulerTest.java
    - src/test/java/org/specdriven/agent/loop/SpecDrivenPipelineTest.java
---

# Autonomous Loop Driver (Delta)

## ADDED Requirements

### Requirement: Loop auto recommend phase

The autonomous loop MUST include a first-class recommend phase before proposal work begins.

#### Scenario: Recommend phase runs first
- GIVEN an autonomous loop iteration has at least one eligible roadmap planned change
- WHEN the iteration starts
- THEN the loop MUST evaluate the recommend phase before the propose phase
- AND the phase order MUST be auditable as `RECOMMEND -> PROPOSE -> IMPLEMENT -> VERIFY -> REVIEW -> ARCHIVE`

#### Scenario: Recommend selects an eligible planned change
- GIVEN a roadmap milestone with a `Planned Changes` entry declared as `planned`
- AND the change is not present in the loop's completed change names
- WHEN loop auto recommend selects the next candidate
- THEN the selected candidate MUST identify that planned change name
- AND it MUST include the source milestone file
- AND it MUST include the milestone goal available from the roadmap

#### Scenario: Recommend skips completed work
- GIVEN a roadmap milestone declared as `complete`
- OR a planned change declared as `complete`
- OR a planned change already present in completed change names
- WHEN loop auto recommend evaluates candidates
- THEN it MUST NOT select that completed milestone or change

#### Scenario: Recommend preserves target milestone filtering
- GIVEN a loop config with a non-empty target milestone filter
- WHEN loop auto recommend evaluates candidates
- THEN it MUST only select planned changes from milestones matching that filter

#### Scenario: Propose uses the recommended candidate
- GIVEN loop auto recommend selected a candidate for an iteration
- WHEN the propose phase starts for the same iteration
- THEN it MUST use the same selected change name and milestone file
- AND it MUST NOT reselect a different roadmap candidate for that iteration

### Requirement: Loop-only no-confirm recommend path

The system MUST provide a recommendation path for autonomous loop execution that does not wait for human confirmation.

#### Scenario: Autonomous loop recommend requires no confirmation
- GIVEN an autonomous loop is running
- AND at least one eligible roadmap planned change exists
- WHEN the recommend phase selects a candidate
- THEN the loop MUST be able to proceed to the propose phase without a human confirmation checkpoint

#### Scenario: Manual recommendation confirmation remains unchanged
- GIVEN a user invokes the manual roadmap recommendation workflow
- WHEN a recommendation is presented
- THEN proposal artifacts MUST NOT be scaffolded until the user explicitly confirms the change name and scope

## MODIFIED Requirements

### Requirement: PipelinePhase enum

Previously: The system MUST define pipeline phases `PROPOSE`, `IMPLEMENT`, `VERIFY`, `REVIEW`, and `ARCHIVE` in execution order.

The system MUST define pipeline phases `RECOMMEND`, `PROPOSE`, `IMPLEMENT`, `VERIFY`, `REVIEW`, and `ARCHIVE` in that execution order.

#### Scenario: Recommend template path
- GIVEN the `RECOMMEND` phase
- WHEN its template resource path is requested
- THEN the path MUST be `/loop-phases/recommend.txt`

#### Scenario: Ordered phases include recommend first
- GIVEN the pipeline phase order is requested
- WHEN `PipelinePhase.ordered()` is called
- THEN `RECOMMEND` MUST be the first phase
- AND `PROPOSE` MUST immediately follow `RECOMMEND`

### Requirement: LoopCandidate record

Previously: The system MUST provide a `LoopCandidate` with change name, milestone file, and milestone goal.

The system MUST provide a `LoopCandidate` with change name, milestone file, milestone goal, and planned change summary.

#### Scenario: Candidate exposes planned change summary
- GIVEN loop auto recommend selects a roadmap planned change with a summary
- WHEN the selected candidate is inspected
- THEN it MUST expose that planned change summary

#### Scenario: Candidate remains backward-compatible for existing callers
- GIVEN an existing caller creates a candidate with change name, milestone file, and milestone goal only
- WHEN the candidate is constructed
- THEN construction MUST still succeed
- AND the planned change summary MUST default to an empty string

### Requirement: Phase instruction template resources

Previously: The system MUST provide five phase instruction templates for propose, implement, verify, review, and archive.

The system MUST provide six phase instruction templates for recommend, propose, implement, verify, review, and archive.

#### Scenario: Recommend template exists
- GIVEN phase instruction templates are loaded from classpath resources
- WHEN the recommend phase template is requested
- THEN a valid UTF-8 template MUST be available at `/loop-phases/recommend.txt`
