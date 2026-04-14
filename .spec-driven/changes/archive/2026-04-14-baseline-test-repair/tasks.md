# Tasks: baseline-test-repair

## Implementation

- [x] Add nested `metadata` scalar lookup support to `SkillMarkdownParser`
- [x] Add parser coverage for `metadata.skill_id`, `metadata.author`, `metadata.type`, and `metadata.version`
- [x] Update skill discovery test fixtures to use the current metadata frontmatter layout
- [x] Fix `CrossLayerConsistencyTest` stub LLM response cursor so multi-turn tool-call tests advance across client instances

## Testing

- [x] Lint: run `mvn compile -pl . -q`
- [x] Unit test: run `mvn -q -Dtest=SkillMarkdownParserTest,SkillAutoDiscoveryTest,RealSkillsIntegrationTest,RealSkillsDiscoveryTest,CrossLayerConsistencyTest test`
- [x] Unit test: run `mvn test -pl . -q`

## Verification

- [x] Run `node /home/wx766/.agents/skills/spec-driven-propose/scripts/spec-driven.js verify baseline-test-repair`
- [x] Verify the active `llm-config-change-audit` full-test blocker is removed
