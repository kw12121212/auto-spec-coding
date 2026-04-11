---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/event/EventType.java
    - src/main/java/org/specdriven/agent/loop/AnswerResolution.java
    - src/main/java/org/specdriven/agent/loop/DefaultLoopDriver.java
    - src/main/java/org/specdriven/agent/loop/IterationStatus.java
    - src/main/java/org/specdriven/agent/loop/LoopIteration.java
    - src/main/java/org/specdriven/agent/loop/LoopIterationStore.java
    - src/main/java/org/specdriven/agent/loop/LoopState.java
    - src/main/java/org/specdriven/agent/question/QuestionDeliveryService.java
  tests:
    - src/test/java/org/specdriven/agent/loop/DefaultLoopDriverTest.java
    - src/test/java/org/specdriven/agent/loop/LoopStateTest.java
---

# Autonomous Loop Driver — Delta Spec: loop-escalation-gate

## MODIFIED Requirements

### Requirement: DefaultLoopDriver — human escalation gate

- `DefaultLoopDriver` MUST provide a constructor accepting `QuestionDeliveryService` in addition to `LoopConfig`, `LoopScheduler`, `LoopPipeline`, `LoopIterationStore`, and `LoopAnswerAgent`
- Existing `DefaultLoopDriver` constructors MUST remain valid and MUST behave as if no question delivery service is configured
- When pipeline returns `IterationResult` with `status=QUESTIONING`, the driver MUST inspect the returned `Question` before invoking `LoopAnswerAgent`
- If the question category is `PERMISSION_CONFIRMATION` or `IRREVERSIBLE_APPROVAL`, the driver MUST NOT invoke `LoopAnswerAgent`
- If the question delivery mode is `PUSH_MOBILE_WAIT_HUMAN` or `PAUSE_WAIT_HUMAN`, the driver MUST NOT invoke `LoopAnswerAgent`
- For human-escalated questions, the driver MUST transition `RUNNING → QUESTIONING → PAUSED`
- For human-escalated questions, the driver MUST publish `LOOP_QUESTION_ESCALATED`
- For human-escalated questions, the driver MUST record a partial `LoopIteration` with `status=QUESTIONING` and a non-empty `failureReason`
- For human-escalated questions, the driver MUST NOT add the paused change name to `completedChangeNames`
- When a question delivery service is configured for a human-escalated question, the driver MUST submit the waiting question to that service
- When no question delivery service is configured, the driver MUST still expose the escalation through loop event metadata
- Questions whose category permits auto reply and whose delivery mode is `AUTO_AI_REPLY` MUST keep the existing `LoopAnswerAgent` resolution behavior

### Requirement: LOOP_QUESTION_ESCALATED metadata

- `LOOP_QUESTION_ESCALATED` metadata MUST include `questionId` (String)
- `LOOP_QUESTION_ESCALATED` metadata MUST include `sessionId` (String)
- `LOOP_QUESTION_ESCALATED` metadata MUST include `changeName` (String)
- `LOOP_QUESTION_ESCALATED` metadata MUST include `category` (String enum name)
- `LOOP_QUESTION_ESCALATED` metadata MUST include `deliveryMode` (String enum name)
- `LOOP_QUESTION_ESCALATED` metadata MUST include `reason` (String)
- `LOOP_QUESTION_ESCALATED` metadata MUST include `routingReason` (String)
- Existing metadata keys for `LOOP_QUESTION_ROUTED` and `LOOP_QUESTION_ANSWERED` MUST NOT be removed

### Requirement: Escalation persistence and resume

- When a human escalation occurs and a `LoopIterationStore` is configured, the driver MUST save the partial `LoopIteration`
- When a human escalation occurs and a `LoopIterationStore` is configured, the driver MUST save `LoopProgress` after transitioning to `PAUSED`
- The saved progress MUST preserve the completed change set as it existed before the paused change
- On `resume()`, the driver MUST continue from persisted progress without treating the escalated change as complete
- Recovered progress MUST allow `SequentialMilestoneScheduler` to select the escalated change again unless it has been completed by a later successful iteration

## ADDED Requirements

### Requirement: Human escalation reason

- A human escalation reason MUST be non-empty
- For human-only categories, the reason MUST explain that the question category requires human approval
- For human delivery modes, the reason MUST explain that the configured delivery mode requires human handling
- For `LoopAnswerAgent` escalation responses, the reason MUST preserve the agent-provided escalation reason
- For absent `LoopAnswerAgent`, the reason MUST remain `no answer agent configured`
