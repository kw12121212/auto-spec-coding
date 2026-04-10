# question-routing-policy

## What

Add the routing-policy layer for M22 question handling. This change defines the observable question categories, the default delivery mode each category maps to, and the escalation rules that prevent high-risk questions from being auto-answered.

## Why

`question-contract-audit` and `orchestrator-question-pause` already established the shared question contract and the runtime pause/resume semantics. The remaining M22 work now needs a stable policy boundary that answers a narrower question: which kinds of questions may be routed to AI, which must wait for a human, and which must be escalated away from auto-reply by default.

Without this routing policy, `answer-agent-runtime` would have to invent its own safety rules for when AI may respond, and `question-delivery-surface` would have to guess which questions belong on a mobile or inline human path. Defining the routing semantics first reduces that risk and gives the later changes one contract to reuse.

## Scope

- Extend the `question-resolution` spec with observable routing behavior for structured questions
- Define a canonical question category model that distinguishes at least clarification, plan-selection guidance, permission confirmation, and irreversible-operation approval questions
- Define the default `DeliveryMode` for each supported category
- Define escalation requirements for categories that MUST remain human-only and MUST NOT be auto-answered by AI
- Define how a routing decision is exposed for auditability before answer execution begins
- Keep the existing `Question` / `Answer` payload contract and waiting semantics intact
- Do not implement the answer agent runtime itself; that remains in `answer-agent-runtime`
- Do not define SDK, HTTP, or JSON-RPC APIs for policy configuration or pending-question retrieval; those remain in `question-delivery-surface`
- Do not bind any mobile provider, webhook adapter, or message template behavior; those remain in M23

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- Existing question payload fields (`question`, `impact`, `recommendation`) and answer audit fields remain unchanged
- Existing waiting-question pause/resume and timeout behavior remain unchanged
- Existing permission model meanings for `ALLOW`, `DENY`, and `CONFIRM` remain unchanged; this change only defines how question handling routes follow-up interaction
