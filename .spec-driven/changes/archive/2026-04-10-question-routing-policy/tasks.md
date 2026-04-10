# Tasks: question-routing-policy

## Implementation

- [x] Extend the `question-resolution` delta spec with a `QuestionCategory` model and the required observable categories for clarification, plan selection, permission confirmation, and irreversible approval
- [x] Define the default routing-policy requirements mapping categories to `DeliveryMode`, including which categories may use `AUTO_AI_REPLY`
- [x] Define escalation and auditability requirements that require human handling for permission-confirmation and irreversible-approval questions

## Testing

- [x] Run `mvn -q -DskipTests compile` as lint / validation to confirm the repository still compiles after implementing the change
- [x] Run `mvn -q test -Dsurefire.useFile=false` as the unit test regression command
- [x] Add unit tests covering category serialization, default routing for each category, rejection of `AUTO_AI_REPLY` for human-only categories, and routing audit metadata

## Verification

- [x] Verify the change only defines routing semantics and does not add answer-agent execution, mobile channel integration, or public SDK / HTTP / JSON-RPC APIs
- [x] Verify existing waiting-question pause/resume semantics remain defined by `orchestrator-question-pause` and are not redefined here
- [x] Verify the new routing contract is sufficient for later `answer-agent-runtime` and `question-delivery-surface` work without redefining categories or human-only boundaries
