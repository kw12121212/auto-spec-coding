# Design: question-routing-policy

## Approach

Add the routing rules as an extension to the existing `question-resolution` spec rather than creating a separate public API surface. The proposal stays intentionally narrow:

1. Define a `QuestionCategory` classification model for the kinds of questions the orchestrator may surface.
2. Define the default `DeliveryMode` expected for each category.
3. Define which categories MAY be auto-answered and which categories MUST be escalated to human handling.
4. Define a minimal routing-decision audit shape so later runtime changes can show why a question took a given path.

This keeps the routing policy at the domain-contract level, where both `answer-agent-runtime` and `question-delivery-surface` can reuse it without prematurely coupling to SDK or transport APIs.

## Key Decisions

- Keep the delta surface in `question-resolution.md` only. The routing policy is part of question-handling behavior, so splitting it into a separate spec file would add indirection without reducing ambiguity.
- Introduce an explicit `QuestionCategory` model. Existing `DeliveryMode` values describe how a question is answered, but not why it was routed that way. The category fills that gap and makes audit reasoning testable.
- Default low-risk clarification and plan-selection questions to `AUTO_AI_REPLY`. These are the clearest candidates for later `answer-agent-runtime` automation.
- Require permission-confirmation and irreversible-operation-approval questions to route to human handling and forbid `AUTO_AI_REPLY`. This preserves the roadmap's stated safety boundary around approvals and risky writes.
- Define routing output as observable policy metadata, not as a transport-specific object. Later SDK or HTTP surfaces can expose the same semantics without redefining the contract.

## Alternatives Considered

- Add routing rules directly to `answer-agent-runtime`: rejected because it would hide core safety rules inside one implementation path instead of making them part of the shared contract.
- Couple routing policy to permission-provider outcomes: rejected because `CONFIRM` already has a stable meaning in the permission model, while M22 routing needs a broader question taxonomy that also covers non-permission clarifications and plan choices.
- Start with `question-delivery-surface` instead: rejected because public APIs would have to expose pending-question behavior before the routing semantics are settled.
