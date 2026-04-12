# Design: tool-result-filter

## Approach

Introduce a small filtering API in the existing `org.specdriven.agent.agent` context package. The API should take explicit current-turn data, the candidate message list, a `ContextRelevanceScorer`, and a `ContextRetentionPolicy`. It should return a filtered immutable message list suitable for constructing a subsequent `LlmRequest`.

The default implementation should scan messages in original order. Non-tool messages stay unchanged. Tool messages are converted into scorer and retention inputs using observable fields such as content, tool name, and tool-call ID. If the retention decision is `MANDATORY`, the message is retained regardless of relevance score. Otherwise, the relevance scorer decides whether the tool message remains eligible for the outgoing request.

The implementation should be usable directly by later changes without requiring immediate orchestrator or loop integration. A helper for applying the filter to an existing `LlmRequest` may be added if it keeps request parameters unchanged while replacing only the filtered message list.

## Key Decisions

- Keep this as an explicit request-preparation component, not hidden behavior in every `LlmClient`, so existing callers remain unchanged unless they opt in.
- Preserve ordering of all retained messages to avoid changing conversation semantics beyond removing irrelevant tool output.
- Let mandatory retention override relevance scoring, matching the existing M27 retention-policy requirement.
- Keep the first implementation keyword/rule based through existing `ContextRelevanceScorer`; embedding relevance remains out of scope.
- Treat summarization as a later change because filtering can be verified independently and has a smaller blast radius.

## Alternatives Considered

- Integrate directly into `DefaultOrchestrator`: ruled out for this change because M27 plans a later `smart-context-injector` integration step, and direct orchestration changes would expand scope.
- Modify provider clients to drop tool messages during serialization: ruled out because filtering should be provider-agnostic and visible before serialization.
- Replace irrelevant tool results with placeholder messages: ruled out because the roadmap item calls for filtering unrelated `ToolResult` output, and placeholder behavior would still consume tokens and complicate provider behavior.
- Wait for `conversation-summarizer`: ruled out because ToolResult filtering already has completed prerequisites and reduces context pressure independently.
