# Tasks: context-retention-policy

## Implementation

- [x] Write `proposal.md`, `design.md`, and `questions.md` for the `context-retention-policy` change with M27-aligned scope boundaries.
- [x] Add a delta spec at `changes/context-retention-policy/specs/llm/context-retention-policy.md` defining retention decisions, retention reasons, and mandatory-context scenarios.
- [x] Include spec frontmatter mappings for likely implementation and unit-test files under `src/main/java/org/specdriven/agent/agent/` and `src/test/java/org/specdriven/agent/agent/`.
- [x] Add `ContextRetentionPolicy`, `ContextRetentionCandidate`, `ContextRetentionDecision`, `ContextRetentionLevel`, `ContextRetentionReason`, and `DefaultContextRetentionPolicy` under `src/main/java/org/specdriven/agent/agent/`.
- [x] Ensure the default policy preserves recovery, question escalation, answer replay, audit traceability, and active tool-call correlation context while leaving ordinary stale context optimizable.
- [x] Keep existing orchestrator, provider, loop, and request-building paths behaviorally unchanged in this change.

## Testing

- [x] Run validation command `node /home/code/.agents/skills/roadmap-recommend/scripts/spec-driven.js verify context-retention-policy` for the proposal artifacts.
- [x] Add `ContextRetentionPolicyTest` under `src/test/java/org/specdriven/agent/agent/` covering mandatory retention reasons, multiple reasons, optional ordinary context, null/empty inputs, and deterministic repeated decisions.
- [x] Run lint or validation command `mvn compile -q`.
- [x] Run unit test command `mvn -Dtest=ContextRetentionPolicyTest test`.
- [x] Run unit test command `mvn test -q -Dsurefire.useFile=false` if the focused test passes.

## Verification

- [x] Confirm the implementation only defines and tests retention policy behavior, without adding filtering, summarization, or smart-injector integration.
- [x] Confirm the delta spec requirements are satisfied by observable tests rather than internal implementation assertions.
