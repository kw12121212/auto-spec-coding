# Design: context-retention-policy

## Approach

Add a small retention-policy contract in the existing LLM/agent context area so later M27 changes can ask one question before optimizing context: "is this item mandatory to keep, and why?" The contract should be explicit about the observable inputs used to make that decision rather than reaching into orchestrator internals.

The proposed policy evaluates context candidates that represent conversation messages, tool results, question payloads, answer payloads, loop recovery records, or audit breadcrumbs. The policy returns a decision with a retention level and one or more retention reasons. Later changes can combine this with `ContextRelevanceScorer`: relevance decides ordering among optional items, while retention decides which items cannot be removed.

The initial implementation should be deterministic and rule-based. It should retain candidates marked as needed for recovery execution, active or waiting question handling, accepted answer replay, audit traceability for the active question/session, and active tool-call correlation. Ordinary stale messages with no retention marker should remain optional or discardable so this policy does not disable future optimization.

## Key Decisions

- Define the retention contract before implementing filtering or summarization, because those later behaviors need a stable safety boundary.
- Treat retention as separate from relevance. A low-relevance item can still be mandatory if it is required for recovery, question handling, answer replay, or auditability.
- Return structured retention reasons instead of a bare boolean so tests and downstream behavior can explain why an item survived optimization.
- Keep the first policy rule-based and deterministic. M27 can later add ranking and summarization without coupling retention to provider-specific semantics.
- Keep integration out of this change. No existing LLM request-building path should change until `tool-result-filter`, `conversation-summarizer`, or `smart-context-injector` explicitly consumes this policy.

## Alternatives Considered

- Rely on `ContextRelevanceScorer` alone.
  - Rejected because relevance scoring cannot safely infer recovery or question lifecycle requirements from keyword overlap.
- Implement `tool-result-filter` first and embed retention rules there.
  - Rejected because summarization and the final injector would need the same rules, causing duplicated behavior and inconsistent edge cases.
- Mark every question or loop-related message as permanently mandatory.
  - Rejected because it would preserve too much stale context and undermine the token-optimization goal.
- Start with provider-specific semantic retention.
  - Rejected because mandatory retention should be explainable, deterministic, and independent of the LLM provider.
