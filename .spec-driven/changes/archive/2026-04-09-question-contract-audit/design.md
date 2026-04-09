# Design: question-contract-audit

## Approach

Add a dedicated `question-resolution` spec file rather than stretching the existing agent or SDK specs to carry the whole feature. The new spec will define the shared domain language and observable lifecycle that all later M22 changes must reuse.

Use the existing event system as the integration point for lifecycle visibility. This change will reserve question-specific event types and define the metadata they must carry, but it will not specify the runtime component that emits them yet.

Keep the proposal intentionally narrow:

1. Define the contract for structured questions and answers
2. Define the answer attribution and audit fields required for traceability
3. Define delivery modes and decision/status enums needed by later routing and delivery work
4. Defer SDK APIs, pause/resume runtime behavior, and mobile adapters to later planned changes

## Key Decisions

- **SDK surface is deferred**: pending-question query and manual answer submission belong to `question-delivery-surface`, not this foundation change. This keeps the contract stable before exposing public APIs.
- **`confidence` and `basisSummary` are required in the base answer contract**: later runtime work should not be allowed to omit core traceability data.
- **`escalationReason` is conditional**: it is required only when a question is escalated or routed for human handling, avoiding meaningless placeholder values on normal answers.
- **No pause/resume semantics in this change**: M22 explicitly splits orchestrator behavior into `orchestrator-question-pause`; this change should not silently pull it forward.
- **No provider-specific delivery details**: `DeliveryMode` values are standardized now, while concrete mobile or webhook integrations remain in M23.

## Alternatives Considered

- **Include SDK APIs now**: rejected because it would couple a still-forming domain model to public SDK surface too early.
- **Start with `orchestrator-question-pause`**: rejected because pause/resume behavior would end up defining the question contract implicitly instead of reusing an explicit shared model.
- **Start with Go or TypeScript client SDK work instead**: rejected because those changes do not unlock the remaining roadmap chain, while M22 is a prerequisite for M23.
