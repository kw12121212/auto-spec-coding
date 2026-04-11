---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/question/DeliveryMode.java
    - src/main/java/org/specdriven/agent/question/Question.java
    - src/main/java/org/specdriven/agent/question/QuestionCategory.java
    - src/main/java/org/specdriven/agent/question/QuestionDeliveryService.java
    - src/main/java/org/specdriven/agent/question/QuestionRoutingPolicy.java
    - src/main/java/org/specdriven/agent/question/QuestionRuntime.java
  tests:
    - src/test/java/org/specdriven/agent/question/QuestionDeliveryServiceTest.java
    - src/test/java/org/specdriven/agent/question/QuestionRoutingPolicyTest.java
    - src/test/java/org/specdriven/agent/question/QuestionRuntimeTest.java
    - src/test/java/org/specdriven/agent/question/QuestionTest.java
---

# Question Resolution — Delta Spec: loop-escalation-gate

## MODIFIED Requirements

### Requirement: Human-only escalation policy — loop consumption

- Questions categorized as `PERMISSION_CONFIRMATION` MUST be observable to the autonomous loop as requiring human handling
- Questions categorized as `IRREVERSIBLE_APPROVAL` MUST be observable to the autonomous loop as requiring human handling
- The routing metadata for human-only categories MUST include a non-empty `routingReason`
- The routing metadata for human-only categories MUST be usable by loop escalation events without requiring a second routing decision
- Human-only categories MUST continue to reject `AUTO_AI_REPLY`

### Requirement: Question delivery surface for loop escalation

- When an escalated loop question has delivery mode `PUSH_MOBILE_WAIT_HUMAN`, the configured question delivery surface MUST make the question available to the configured mobile delivery channel
- When an escalated loop question has delivery mode `PAUSE_WAIT_HUMAN`, the configured question delivery surface MUST make the question available as a pending waiting question for the session
- If no delivery surface is configured for the loop, the question MUST remain observable through loop escalation event metadata
- Delivery failures MUST NOT mark the loop iteration successful
- Delivery failures MUST leave the loop in `PAUSED` or `ERROR`, never `RUNNING`

## ADDED Requirements

### Requirement: Loop escalation audit context

- An escalated loop question MUST expose enough audit context to identify the originating session
- An escalated loop question MUST expose enough audit context to identify the question category and delivery mode
- An escalated loop question MUST expose enough audit context to explain why AI auto-answering was not used
- The audit context MUST preserve the original `questionId`, `sessionId`, `category`, `deliveryMode`, and `routingReason`
