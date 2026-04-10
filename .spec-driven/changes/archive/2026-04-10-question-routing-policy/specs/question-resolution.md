# Question Resolution Spec (delta)

## ADDED Requirements

### Requirement: Question category model

The system MUST classify structured questions into observable categories before routing them for an answer.

#### Scenario: Required question categories
- THEN the system MUST define `QuestionCategory.CLARIFICATION`
- AND it MUST define `QuestionCategory.PLAN_SELECTION`
- AND it MUST define `QuestionCategory.PERMISSION_CONFIRMATION`
- AND it MUST define `QuestionCategory.IRREVERSIBLE_APPROVAL`

#### Scenario: Question exposes category
- GIVEN a `Question` instance created for routing
- THEN it MUST expose a `QuestionCategory`
- AND the category MUST be included in the question's canonical structured payload as an enum name

### Requirement: Default routing policy

The system MUST define a default routing policy from `QuestionCategory` to `DeliveryMode`.

#### Scenario: Clarification defaults to auto AI reply
- GIVEN a structured question categorized as `CLARIFICATION`
- WHEN the default routing policy is applied
- THEN the selected `DeliveryMode` MUST be `AUTO_AI_REPLY`

#### Scenario: Plan selection defaults to auto AI reply
- GIVEN a structured question categorized as `PLAN_SELECTION`
- WHEN the default routing policy is applied
- THEN the selected `DeliveryMode` MUST be `AUTO_AI_REPLY`

#### Scenario: Permission confirmation defaults to human handling
- GIVEN a structured question categorized as `PERMISSION_CONFIRMATION`
- WHEN the default routing policy is applied
- THEN the selected `DeliveryMode` MUST NOT be `AUTO_AI_REPLY`
- AND the selected `DeliveryMode` MUST be `PAUSE_WAIT_HUMAN`

#### Scenario: Irreversible approval defaults to human handling
- GIVEN a structured question categorized as `IRREVERSIBLE_APPROVAL`
- WHEN the default routing policy is applied
- THEN the selected `DeliveryMode` MUST NOT be `AUTO_AI_REPLY`
- AND the selected `DeliveryMode` MUST be `PAUSE_WAIT_HUMAN`

### Requirement: Human-only escalation policy

The system MUST prevent human-only question categories from being auto-answered.

#### Scenario: Permission confirmation cannot be auto-answered
- GIVEN a structured question categorized as `PERMISSION_CONFIRMATION`
- WHEN an auto-answer route is requested
- THEN the system MUST reject `AUTO_AI_REPLY` for that question
- AND it MUST preserve a human-handled delivery mode for the unresolved question

#### Scenario: Irreversible approval cannot be auto-answered
- GIVEN a structured question categorized as `IRREVERSIBLE_APPROVAL`
- WHEN an auto-answer route is requested
- THEN the system MUST reject `AUTO_AI_REPLY` for that question
- AND it MUST preserve a human-handled delivery mode for the unresolved question

### Requirement: Routing decision auditability

The system MUST make the routing decision observable before answer execution begins.

#### Scenario: Question payload includes routing basis
- GIVEN a structured question prepared for routing
- WHEN its canonical structured payload is requested
- THEN the payload MUST include `category`
- AND it MUST include `deliveryMode`

#### Scenario: Audit metadata records routed category
- GIVEN a question that has been routed for answer handling
- THEN the audit record or emitted metadata MUST include the routed `category`
- AND it MUST include the selected `deliveryMode`

#### Scenario: Escalated human-only route records why AI was not used
- GIVEN a question categorized as `PERMISSION_CONFIRMATION` or `IRREVERSIBLE_APPROVAL`
- WHEN the question is routed for human handling
- THEN the routing metadata MUST explain that the category requires human approval
