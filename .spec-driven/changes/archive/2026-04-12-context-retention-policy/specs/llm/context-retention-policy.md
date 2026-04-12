---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/agent/ContextRetentionCandidate.java
    - src/main/java/org/specdriven/agent/agent/ContextRetentionDecision.java
    - src/main/java/org/specdriven/agent/agent/ContextRetentionLevel.java
    - src/main/java/org/specdriven/agent/agent/ContextRetentionPolicy.java
    - src/main/java/org/specdriven/agent/agent/ContextRetentionReason.java
    - src/main/java/org/specdriven/agent/agent/DefaultContextRetentionPolicy.java
  tests:
    - src/test/java/org/specdriven/agent/agent/ContextRetentionPolicyTest.java
---

# Context Retention Policy

## ADDED Requirements

### Requirement: ContextRetentionPolicy contract
The system MUST provide a `ContextRetentionPolicy` contract for deciding whether a context candidate is mandatory to retain before context optimization is allowed to remove or compress it.

#### Scenario: Decide retention for a candidate
- GIVEN a retention policy
- AND a context candidate with observable metadata
- WHEN the caller asks the policy to evaluate the candidate
- THEN the policy MUST return a deterministic retention decision
- AND the decision MUST expose a retention level
- AND the decision MUST expose zero or more retention reasons

#### Scenario: Deterministic decision for identical inputs
- GIVEN the same context candidate metadata
- WHEN the retention policy evaluates the candidate repeatedly
- THEN it MUST return the same retention level and retention reasons each time

### Requirement: Retention levels
The system MUST classify each context candidate as `MANDATORY`, `OPTIONAL`, or `DISCARDABLE`.

#### Scenario: Mandatory context is identified
- GIVEN a context candidate required for recovery, question handling, answer replay, audit traceability, or active tool-call correlation
- WHEN the retention policy evaluates the candidate
- THEN the decision retention level MUST be `MANDATORY`

#### Scenario: Ordinary stale context is not mandatory
- GIVEN a context candidate that is not marked as needed for recovery, question handling, answer replay, audit traceability, or active tool-call correlation
- WHEN the retention policy evaluates the candidate
- THEN the decision retention level MUST NOT be `MANDATORY`

### Requirement: Retention reasons
The system MUST expose retention reasons so downstream context optimization can explain why a candidate was preserved.

#### Scenario: Recovery context reason
- GIVEN a context candidate marked as required to resume an interrupted loop or agent execution
- WHEN the retention policy evaluates the candidate
- THEN the decision MUST include retention reason `RECOVERY_EXECUTION`
- AND the decision retention level MUST be `MANDATORY`

#### Scenario: Question escalation reason
- GIVEN a context candidate belonging to an open, waiting, or escalated question lifecycle
- WHEN the retention policy evaluates the candidate
- THEN the decision MUST include retention reason `QUESTION_ESCALATION`
- AND the decision retention level MUST be `MANDATORY`

#### Scenario: Answer replay reason
- GIVEN a context candidate containing an accepted answer that must be replayed into the resumed conversation
- WHEN the retention policy evaluates the candidate
- THEN the decision MUST include retention reason `ANSWER_REPLAY`
- AND the decision retention level MUST be `MANDATORY`

#### Scenario: Audit trace reason
- GIVEN a context candidate that provides audit traceability for the active question, answer, or recovery session
- WHEN the retention policy evaluates the candidate
- THEN the decision MUST include retention reason `AUDIT_TRACE`
- AND the decision retention level MUST be `MANDATORY`

#### Scenario: Active tool-call reason
- GIVEN a context candidate containing a tool result correlated with an active or unresolved tool call
- WHEN the retention policy evaluates the candidate
- THEN the decision MUST include retention reason `ACTIVE_TOOL_CALL`
- AND the decision retention level MUST be `MANDATORY`

### Requirement: Multiple retention reasons
A single context candidate MAY be retained for more than one reason.

#### Scenario: Candidate has multiple mandatory reasons
- GIVEN a context candidate marked as both recovery context and answer replay context
- WHEN the retention policy evaluates the candidate
- THEN the decision MUST include both `RECOVERY_EXECUTION` and `ANSWER_REPLAY`
- AND the decision retention level MUST be `MANDATORY`

### Requirement: Retention takes precedence over relevance
Mandatory retention MUST take precedence over current-turn relevance scores.

#### Scenario: Low-relevance mandatory context remains mandatory
- GIVEN a context candidate with no keyword or tool-name overlap with the current turn
- AND the candidate is required for recovery execution
- WHEN the retention policy evaluates the candidate
- THEN the decision retention level MUST be `MANDATORY`

#### Scenario: Relevance does not create mandatory retention by itself
- GIVEN a context candidate with keyword or tool-name overlap with the current turn
- AND the candidate is not required for recovery, question handling, answer replay, audit traceability, or active tool-call correlation
- WHEN the retention policy evaluates the candidate
- THEN the decision retention level MUST NOT be `MANDATORY`

### Requirement: Null and empty candidate handling
The default retention policy MUST handle absent optional metadata without throwing.

#### Scenario: Empty metadata is optimizable
- GIVEN a context candidate with no retention flags, no session identifier, and no tool-call identifier
- WHEN the default retention policy evaluates the candidate
- THEN the decision retention level MUST NOT be `MANDATORY`
- AND the decision MUST contain no retention reasons

#### Scenario: Missing candidate is rejected
- GIVEN a null context candidate
- WHEN the default retention policy evaluates the candidate
- THEN the policy MUST reject the input with a descriptive runtime exception

### Requirement: No request-building behavior change
Defining the retention policy MUST NOT change existing LLM request construction or provider behavior until a later integration change consumes the policy.

#### Scenario: Existing LLM calls are unchanged
- GIVEN existing code that constructs an `LlmRequest`
- WHEN this change is applied
- THEN the request MUST contain the same messages, tool schemas, system prompt, and generation parameters as before unless the caller explicitly invokes the retention policy
