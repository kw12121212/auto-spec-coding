# smart-context-injector

## What

Add the M27 `smart-context-injector` change. This change integrates the completed M27 context components into a transparent LLM request-preparation layer: tool-result filtering runs before conversation summarization, mandatory retention rules remain authoritative, and optimized requests are sent through the existing `LlmClient` path without changing orchestrator call signatures.

## Why

M27 already has `ContextRelevanceScorer`, `ContextRetentionPolicy`, `ToolResultFilter`, and `ConversationSummarizer`. Those components are currently usable as isolated opt-in utilities, but the main autonomous execution path still passes conversation history directly to the LLM client. The remaining roadmap item is the integration step that makes context optimization observable in long-running loops while preserving recovery, question escalation, answer replay, audit traceability, and active tool-call context.

Finishing this item closes M27 before starting broader new milestones such as dynamic LLM configuration, hot loading, ORM migration, or platform unification. It also reduces context-window risk for later autonomous loop work.

## Scope

In scope:

- Define observable `SmartContextInjector` behavior as an `LlmClient` decorator for optimizing outgoing message lists before delegate calls.
- Compose `ToolResultFilter` and `ConversationSummarizer` in a deterministic order: filter irrelevant non-mandatory tool results first, then summarize older eligible history.
- Preserve request parameters and delegate responses while changing only the outgoing message list.
- Preserve mandatory context classified by `ContextRetentionPolicy` across both filtering and summarization.
- Provide deterministic defaults for current-turn extraction and context budgets, with explicit metadata support when available.
- Integrate smart context optimization into the spec-driven autonomous pipeline path when loop context budgeting is configured, without changing `DefaultOrchestrator.run(...)` signatures.
- Add a fixed evaluation fixture and acceptance threshold proving at least 30% estimated token reduction without losing critical mandatory-context markers.
- Add focused JUnit 5 tests covering decorator behavior, composition order, mandatory-retention precedence, request-parameter preservation, loop/pipeline integration, and evaluation thresholds.

Out of scope:

- New embedding-based relevance scoring.
- LLM-generated abstractive summarization.
- Provider-specific serialization changes.
- Dynamic LLM configuration or provider hot switching.
- Changing question routing, answer replay, or audit lifecycle semantics.
- Replacing `ContextWindowManager` token usage tracking.

## Unchanged Behavior

Behaviors that must not change as a result of this change:

- Existing callers that pass a plain, unwrapped `LlmClient` must see unchanged request contents.
- `DefaultOrchestrator.run(AgentContext, LlmClient)` must keep its public signature and state behavior.
- Existing LLM provider clients must continue serializing `LlmRequest` objects with the same provider-specific behavior.
- Existing `ContextRelevanceScorer`, `ContextRetentionPolicy`, `ToolResultFilter`, and `ConversationSummarizer` behavior must remain backward compatible.
- Loop context exhaustion tracking must continue to use observed `LlmResponse` usage and must remain disabled when no context budget is configured.
