# Design: baseline-test-repair

## Approach

1. Update `SkillMarkdownParser` to read scalar fields from the top-level map first and then from the nested `metadata` map. Use this for `skill_id`, `author`, `type`, and `version`.
2. Add parser test coverage for the current metadata layout while retaining existing top-level coverage.
3. Update discovery fixture generation to use the metadata layout so discovery tests cover the current real-world format.
4. Change `CrossLayerConsistencyTest.StubLlmProvider` to keep a shared response cursor across client instances. Each created `StubLlmClient` will ask the provider for the next response instead of owning a fresh cursor.

## Key Decisions

- Keep top-level fallback for backward compatibility because older tests and local SKILL.md files may still use the previous frontmatter layout.
- Keep `name` top-level because the current real skills still expose it there and service naming depends on it.
- Fix CrossLayer through the test fixture, not production orchestration, because the failure is caused by the stub resetting its response sequence on every client creation.

## Alternatives Considered

- Require only nested `metadata` fields and remove top-level compatibility. Rejected because it would break existing valid fixtures without adding user-visible value.
- Change the cross-layer test to provide repeated tool-call responses. Rejected because it would hide the intended two-step tool-call round trip instead of making the stub behave like one logical provider sequence.
