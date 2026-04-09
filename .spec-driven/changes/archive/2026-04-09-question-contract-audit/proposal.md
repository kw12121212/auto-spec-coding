# question-contract-audit

## What

Add the foundational question-resolution contract for M22. This change defines the observable domain model for `Question`, `Answer`, `AnswerSource`, `QuestionStatus`, `QuestionDecision`, and `DeliveryMode`, establishes the minimum structured question payload fields, and specifies the audit and event metadata that later runtime changes must produce.

## Why

M22 is the next unfinished milestone that unlocks M23. Its later planned changes depend on a stable contract for what a question is, what an answer must contain, and how question lifecycle events are identified and audited.

Without this contract, `orchestrator-question-pause`, `answer-agent-runtime`, `question-routing-policy`, and `question-delivery-surface` would each risk defining their own incompatible payloads and audit fields. This change fixes that by defining the shared observable behavior first.

## Scope

- Add a new `question-resolution` spec describing:
  - core question and answer types
  - required structured payload fields: `question`, `impact`, `recommendation`
  - required answer attribution fields: `source`, `basisSummary`, `confidence`
  - conditional escalation metadata requirements
  - lifecycle states and delivery modes
- Extend the event model to reserve explicit question lifecycle event types
- Define the minimum audit fields needed to explain who answered a question, why, and under which delivery mode or escalation path
- Keep SDK-facing pending-question APIs and manual answer submission out of this change
- Keep orchestrator pause/resume behavior out of this change
- Keep mobile channel integrations and concrete provider adapters out of this change

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- Existing `SdkAgent.run()` behavior remains unchanged; this change does not add new SDK query or reply APIs
- Existing agent lifecycle behavior remains unchanged; no new pause/resume execution behavior is introduced here
- Existing permission confirmation behavior remains unchanged; this change only defines question contracts and audit expectations
