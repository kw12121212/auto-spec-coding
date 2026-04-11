# Tasks: context-relevance-scorer

## Implementation

- [x] Write `proposal.md`, `design.md`, and `questions.md` for the `context-relevance-scorer` change with M27-aligned scope boundaries
- [x] Add a delta spec at `changes/context-relevance-scorer/specs/llm/context-relevance-scorer.md` defining the scorer contract, default keyword-based behavior, and stable ordering semantics
- [x] Include spec frontmatter mappings for the likely implementation and unit-test files under `src/main/java/org/specdriven/agent/agent/` and `src/test/java/org/specdriven/agent/agent/`
- [x] Add `ContextRelevanceScorer` and `KeywordContextRelevanceScorer` under `src/main/java/org/specdriven/agent/agent/` with explicit current-turn inputs and deterministic numeric scoring
- [x] Add `ContextRelevanceScorerTest` under `src/test/java/org/specdriven/agent/agent/` covering tool-name matches, keyword overlap, normalization, empty-input cases, and stable equal-score behavior

## Testing

- [x] Run validation command `node /home/code/.agents/skills/spec-driven-auto/scripts/spec-driven.js verify context-relevance-scorer`
- [x] Run unit test command `mvn -Dtest=ContextRelevanceScorerTest test` after implementation is added

## Verification

- [x] Confirm the proposed change remains limited to scorer specification and does not silently expand into filtering, summarization, or injector integration
