# context-retention-policy

## What

Define the M27 context-retention contract that later filtering and summarization changes must obey before reducing the context sent to an LLM.

This change introduces observable rules for identifying context that MUST be retained during recovery execution, human question escalation, and answer replay. It also defines how retention decisions expose reasons so later `tool-result-filter`, `conversation-summarizer`, and `smart-context-injector` changes can preserve required context without duplicating policy rules.

## Why

M27 aims to replace simple context truncation with smarter filtering and summarization. That optimization is risky unless the system first defines which context cannot be trimmed. Recovery, question escalation, and answer replay already rely on persisted state, question payloads, answers, and phase progress; losing any of that context can make a resumed loop behave differently from the interrupted one.

The existing `context-relevance-scorer` change only ranks prior tool results. It does not define safety boundaries for context that must survive even when it scores low for the current turn. This proposal fills that gap before any behavior starts dropping or compressing messages.

## Scope

- In scope: a retention policy contract for deciding whether a context item is mandatory, optional, or discardable for optimization.
- In scope: retention reasons for recovery execution, question escalation, answer replay, audit traceability, and active tool-call correlation.
- In scope: observable behavior for preserving mandatory context even when relevance scoring would otherwise rank it low.
- In scope: unit-testable edge cases for null or empty inputs, multiple reasons, stable decisions, and no false retention for ordinary stale context.
- Out of scope: actually filtering `ToolMessage` instances from outbound `LlmRequest` values.
- Out of scope: conversation summarization, summary generation, summary persistence, or token-budget benchmarking.
- Out of scope: integrating a smart context injector into `DefaultOrchestrator`, `SpecDrivenPipeline`, or provider clients.
- Out of scope: provider-specific semantic ranking or embedding-based retention decisions.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):

- Existing `LlmClient`, provider serialization, and `LlmRequest` construction behavior remains unchanged.
- Existing `ContextRelevanceScorer` scoring behavior remains unchanged.
- Existing `ContextWindowManager` token accounting and answer-agent cropping behavior remains unchanged.
- Existing loop pause, question routing, answer handling, and recovery behavior remains unchanged until a later integration change consumes the retention policy.
