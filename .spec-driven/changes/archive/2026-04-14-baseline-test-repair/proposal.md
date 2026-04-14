# baseline-test-repair

## What

Repair the current full-test baseline by accepting the current SKILL.md frontmatter layout and by fixing the cross-layer integration test fixture so a multi-turn tool-call flow can consume its configured response sequence across client instances.

## Why

`mvn test -pl . -q` currently fails for two baseline reasons outside `llm-config-change-audit`:

- Real skill files now place `skill_id`, `author`, `type`, and `version` under `metadata`, while `SkillMarkdownParser` still requires top-level `skill_id`.
- `CrossLayerConsistencyTest` resets the stub LLM response cursor every time the registry creates a new client, so tool-call orchestration receives the first `ToolCallResponse` repeatedly instead of advancing to the final text response.

## Scope

- Accept `metadata.skill_id`, `metadata.author`, `metadata.type`, and `metadata.version` when parsing SKILL.md frontmatter.
- Preserve compatibility with existing top-level skill metadata fields.
- Keep `name` and `description` as top-level frontmatter fields.
- Fix the `CrossLayerConsistencyTest` stub LLM fixture so configured responses advance across client instances created during one logical agent run.
- Do not change production HTTP, SDK, JSON-RPC, or LLM orchestration behavior.
- Do not modify the active `llm-config-change-audit` change beyond allowing the full baseline to pass later.

## Unchanged Behavior

- SKILL.md files without valid frontmatter still fail parsing.
- `name` remains required.
- Existing top-level `skill_id` SKILL.md files remain accepted.
- Skill SQL generation continues to use the parsed `SkillFrontmatter` fields.
- Cross-layer tests continue to exercise SDK and HTTP through the same shared test SDK and stub echo tool.
